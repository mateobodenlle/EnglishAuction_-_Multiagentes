package com.mateobodenlle.buyer;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class BuyerAgent extends Agent {
    private double presupuestoMaximo = 80.0;
    private BuyerController controller;
    @Override
    protected void setup() {
        System.out.println("Comprador " + getLocalName() + " iniciado.");

        // Setup interfaz gráfica
        controller = BuyerController.getInstance();
        controller.setAgente(this);
        controller.actualizarTope(String.valueOf(presupuestoMaximo));

        // Registrarse con el vendedor
        ACLMessage registerMsg = new ACLMessage(ACLMessage.INFORM);
        registerMsg.addReceiver(new jade.core.AID("Vendedor", jade.core.AID.ISLOCALNAME));
        registerMsg.setContent("Registro");
        send(registerMsg);

        // Participar en la subasta
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(); // Recibir Call For Proposal del vendedor, con el precio (cada 10s)
                if (msg != null && msg.getPerformative() == ACLMessage.CFP) { // Comprobamos que el mensaje sea de CFP
                    String contenido = msg.getContent();
                    double precioActual = Double.parseDouble(contenido.split(": ")[1]);

//                    if (contenido.split(": ")[0].equals("Has ganado la subasta con una puja de")) {
//                        System.out.println("Has ganado la subasta con una puja de: " + precioActual);
//                        controller.setLabelEstadoText("GANADOR");
//                        controller.añadirMensajeExterno("Ganador!\n Puja final de: " + precioActual);
//                        doDelete();
//                        return;
//                    }

                    // Display por ventana del precio recibido
                    controller.añadirPrecio(String.valueOf(precioActual));
                    controller.actualizarPrecio(String.valueOf(precioActual));

                    // Decisión de puja
                    if (precioActual <= presupuestoMaximo) { // Si está dentro del presupuesto, puja
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent("Puja: " + precioActual);
                        send(reply);
                        controller.añadirPuja(String.valueOf(precioActual));
                        System.out.println(getLocalName() + " envió puja: " + precioActual);
                    } else { // Si no está dentro del presupuesto, no puja.
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent("No puja: " + precioActual);
                        send(reply);
                        System.out.println(getLocalName() + " no puja. Precio demasiado alto.");
                    }

                } else if (msg != null && msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) { // Si el vendedor acepta la puja
                    String contenido = msg.getContent();
                    double precioFinal = Double.parseDouble(contenido.split(": ")[1]);
                    controller.setLabelEstadoText("GANADOR:"+precioFinal);
                    controller.añadirMensajeExterno("Ganador!\n Puja final de: " + precioFinal);


                    //todo, transacción de pago (simulada...). Iniciativa del vendedor
                    ACLMessage transaccion = blockingReceive();

                    controller.añadirMensajeExterno("Petición de transacción del vendedor: " + transaccion.getContent());


                    doDelete();

                }else if (msg != null && msg.getPerformative() == ACLMessage.REJECT_PROPOSAL) { // Si el vendedor rechaza la puja
                    String contenido = msg.getContent();
                    double precioFinal = Double.parseDouble(contenido.split(": ")[1]);
                    controller.setLabelEstadoText("PERDEDOR:" + precioFinal);
                    controller.añadirMensajeExterno("PERDEDOR!\n Puja final de: " + precioFinal);

                    //doDelete();
                }else {
                    block();
                }
            }
        });


    }

    public void setTope(double tope) {
        presupuestoMaximo = tope;
    }
}
