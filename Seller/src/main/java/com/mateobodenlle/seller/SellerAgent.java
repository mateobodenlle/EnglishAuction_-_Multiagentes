package com.mateobodenlle.seller;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;


public class SellerAgent extends Agent {
    private double precioActual = 20.0;
    private double incremento = 10.0;
    private Set<AID> compradoresRegistrados = new HashSet<>();
    private ArrayList<ACLMessage> pujas = new ArrayList<>(); // Registro de pujas
    private boolean subastaActiva = false;

    private SellerController controller;



    // Llamado desde el hilo de FX
    public void iniciarSubasta() {
        subastaActiva = true;
        controller.actualizarPrecio(String.valueOf(precioActual));
    }

    @Override
    protected void setup() {
        System.out.println("Agente Vendedor iniciado: " + getAID().getName());

        // Controlador FX
        controller = SellerController.getInstance();
        controller.setSellerAgent(this);
        // Comportamiento para manejar registros de nuevos compradores
        addBehaviour(new CyclicBehaviour() {
            // Recibir mensajes de registro de nuevos compradores
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM) {
                        AID nuevoComprador = msg.getSender();
                        System.out.println("Nuevo comprador registrado: " + nuevoComprador.getLocalName());
                        compradoresRegistrados.add(nuevoComprador);
                        controller.añadirComprador(nuevoComprador.getLocalName());
                    }
                } else {
                    block();
                }
            }
        });

        // Comportamiento para enviar el precio actual a los compradores
        addBehaviour(new TickerBehaviour(this, 2000) { // Espera de 10 segundos entre ejecuciones
            private Boolean pujaRecibida = true; // Empieza en true para no terminar en la primera ronda
            @Override
            protected void onTick() {
                // Esperamos a que se pulse el botón de empezar subasta
                if (!subastaActiva) {
                    return;
                }

                // Comprobamos si el precio anterior ha tenido pujas
                if (!pujaRecibida) {
                    finalizar();
                    return;
                }
                // Actualizamos la interfaz gráfica
                controller.actualizarPrecio(String.valueOf(precioActual));


                // Enviamos CFP del precio a los compradores registrados
                ACLMessage cfpPrecio = new ACLMessage(ACLMessage.CFP);
                cfpPrecio.setContent("Precio: " + precioActual);
                for (AID comprador : compradoresRegistrados) {
                    cfpPrecio.addReceiver(comprador);
                }
                send(cfpPrecio);
                pujaRecibida = false;

                // Esperamos las respuestas de los compradores
                ACLMessage respuesta = null;
                for (AID _ : compradoresRegistrados){
                    respuesta = blockingReceive(100);
                    if (respuesta == null) {
                        System.out.println("No se recibió respuesta de comprador.");
                        continue;
                    }

                    if (respuesta!= null && respuesta.getPerformative() == ACLMessage.PROPOSE){
                        String contenido = respuesta.getContent();
                        if (contenido.split(":")[0].equals("Puja")) {
                            double puja = Double.parseDouble(contenido.split(": ")[1]);

                            // Guardamos la puja
                            pujas.add(respuesta);

                            // Actualizamos la interfaz gráfica
                            controller.añadirPuja(respuesta.getSender().getLocalName(), puja);

                            pujaRecibida = true;
                        }
                        else {
                            // Not understood
                            ACLMessage reply = respuesta.createReply();
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            send(reply);
                        }
                    }
            }
                // Actualizamos el precio actual
                precioActual+=incremento;
                }

            private void finalizar() {
                ACLMessage pujaGanadora = null;
                // Actualizamos la interfaz gráfica
                controller.añadirPuja("No hay ninguna puja a: ", precioActual-incremento);

                // Buscamos la puja ganadora (primero con el último precio con pujas)
                AID ganador = null;
                for (ACLMessage propuesta : pujas) { // todo modificar para notificar a los perdedores, para actualizar la interfaz gráfica
                    double precioPropuesta = Double.parseDouble(propuesta.getContent().split(": ")[1]);
                    if (precioPropuesta == (precioActual-2*incremento)) {
                        System.out.println("Puja: " + precioPropuesta);
                        ganador = propuesta.getSender();
                        pujaGanadora = propuesta;
                        break;
                    }
                }
                for (ACLMessage propuesta : pujas) {
                    if (!propuesta.equals(pujaGanadora)) {
                        ACLMessage respuesta = propuesta.createReply();
                        respuesta.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        respuesta.setContent("Has perdido la subasta con una puja de: " + Double.parseDouble(propuesta.getContent().split(": ")[1]));
                        send(respuesta);
                    }
                }

                if (ganador != null) {
                    // Actualizamos la interfaz gráfica
                    controller.añadirPuja("El ganador es " + ganador.getLocalName() + " con una puja de ", precioActual-2*incremento);

                    // Notificamos al ganador
                    notificarGanador(pujaGanadora, precioActual-2*incremento);
                } else {
                    // Si falla algo, no hay ganador
                    System.out.println("No hubo ganador.");
                }

                // Actualizamos la interfaz gráfica
                controller.precioFinal(String.valueOf(precioActual-2*incremento));
                //precioActual-=2*incremento;
                doDelete();
            }

            private void notificarGanador(ACLMessage pujaGanadora, double v) {
                ACLMessage respuesta = pujaGanadora.createReply();
                respuesta.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                respuesta.setContent("Has ganado la subasta con una puja de: " + v);
                send(respuesta);
            }

            private void iniciarTransaccion(ACLMessage pujaGanadora) {
                // Iniciar transacción
                ACLMessage transaccion = new ACLMessage(ACLMessage.REQUEST);
                transaccion.setContent("Transacción de " + pujaGanadora.getSender().getLocalName() + " por " + (precioActual-incremento));
                transaccion.addReceiver(pujaGanadora.getSender());
                send(transaccion);
            }
        });


    }
}
