package com.mateobodenlle.seller;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class SellerAgent extends Agent {
    private double precioActual = 20.0;
    private double incremento = 10.0;
    private Set<AID> compradoresRegistrados = new HashSet<>();
    private Map<AID, Double> pujas = new HashMap<>(); // Registro de pujas
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
            private boolean primeraEjecucion = true;

            @Override
            protected void onTick() {
                if (!subastaActiva) return;
                if (primeraEjecucion) {
                    System.out.println("Esperando antes de enviar el primer precio...");
                    primeraEjecucion = false;
                }

                //Comprobamos si se ha llegado al precio máximo
                else if (!comprobarPuja(precioActual-incremento)) {
                    //System.out.println("Primera" + primeraEjecucion +". Compradores "+compradoresRegistrados.size());
                    System.out.println("No hay pujas. Finalizando subasta.");
                    finalizar();
                    return;
                }

                if (!compradoresRegistrados.isEmpty()) {
                    System.out.println("Enviando precio actual a los compradores...");
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (AID comprador : compradoresRegistrados) {
                        cfp.addReceiver(comprador);
                    }
                    cfp.setContent("Precio actual: " + precioActual);

                    //Actualizamos la UI
                    controller.actualizarPrecio(String.valueOf(precioActual));
                    send(cfp);
                    System.out.println("Precio enviado: " + precioActual);

                    // Recibir pujas
                    ACLMessage respuesta = null;
                    for (AID _ : compradoresRegistrados){
                        respuesta = blockingReceive();

                    //System.out.println("Recibiendo mensaje");
                    //if (respuesta!=null)
                        //System.out.println("Mensaje recibido: "+respuesta.getContent());
                    if (respuesta != null && respuesta.getPerformative() == ACLMessage.PROPOSE) {
                        String contenido = respuesta.getContent();
                        if (contenido.split(":")[0].equals("Puja")) {
                            double puja = Double.parseDouble(contenido.split(":")[1]);
                            pujas.put(respuesta.getSender(), puja);
                            //System.out.println("Puja recibida de " + msg.getSender().getLocalName() + ": " + puja);
                            controller.añadirPuja(respuesta.getSender().getLocalName(), puja);
                        }
                        else if (contenido.split(":")[0].equals("No puja")) {
                            System.out.println("No puja recibida de " + respuesta.getSender().getLocalName());
                        }


                    } else { // todo, igual sobra
                        block();
                    }
                }


                    precioActual += incremento; // Incrementar precio para la siguiente ronda
                } else {
                    primeraEjecucion = true;
                    System.out.println("No hay compradores registrados todavía.");
                }
            }

            private void finalizar() {
                System.out.println("Subasta finalizada.");
                controller.añadirPuja("No hay ninguna puja a: ", precioActual-incremento);
                AID ganador = null;
                for (Map.Entry<AID, Double> entry : pujas.entrySet()) {
                    if (entry.getValue() == precioActual-2*incremento) {
                        System.out.println("Puja: " + entry.getValue());
                        ganador = entry.getKey();
                        break;
                    }
                }
                if (ganador != null) {
                    System.out.println("El ganador es " + ganador.getLocalName() + " con una puja de " + (precioActual-2*incremento) + ".");
                    controller.añadirPuja("El ganador es " + ganador.getLocalName() + " con una puja de ", precioActual-2*incremento);
                    notificarGanador(ganador, precioActual-2*incremento);
                } else {
                    System.out.println("No hubo ganador.");
                }
                System.out.println("Precio final: " + (precioActual-2*incremento));
                controller.precioFinal(String.valueOf(precioActual-2*incremento));
                //precioActual-=2*incremento;
                doDelete();
            }

            private void notificarGanador(AID ganador, double v) {
                ACLMessage inform = new ACLMessage(ACLMessage.CFP);
                inform.addReceiver(ganador);
                inform.setContent("Has ganado la subasta con una puja de: " + v);
                send(inform);
            }
        });


    }

    public Boolean comprobarPuja(double puja) {
        for (Map.Entry<AID, Double> entry : pujas.entrySet()) {
            if (entry.getValue() == puja) {
                return true;
            }
    }
        return false;
    }
}
