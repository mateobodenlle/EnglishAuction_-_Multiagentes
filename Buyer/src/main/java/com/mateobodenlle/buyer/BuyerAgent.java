package com.mateobodenlle.buyer;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;

// Importamos la clase Subasta
import com.mateobodenlle.englishauction.Subasta;

public class BuyerAgent extends Agent {
    // Subastas
    private HashMap<Subasta, Boolean> subastas= new HashMap<>(); // Nombre subasta y si se está suscrito o no
    private String subastaActual = "";
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
                ACLMessage msg = receive();
                if (msg != null && msg.getPerformative() == ACLMessage.CFP) {
                    // Si es un CFP es un anuncio de precio a una subasta suscrita
                    gestionarMensajeCFP(msg);
                } else if (msg != null && msg.getPerformative() == ACLMessage.INFORM) {
                    // Si el vendedor informa de una nueva subasta
                    gestionarMensajeInform(msg);
                }else if (msg != null && msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    // Si el vendedor acepta la puja, es que se ha ganado
                    gestionarMensjeAccept_Proposal(msg);
                }else if (msg != null && msg.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    // Si el vendedor rechaza la puja, es que no se ha ganado
                    gestionarMensajeReject_Proposal(msg);
                }else {
                    block();
                }
            }

            /**
             * Gestiona los mensajes de tipo REJECT_PROPOSAL
             * Se dan cuando el vendedor rechaza la puja del comprador. Es decir, este ha perdido
             * @param msg
             */
            private void gestionarMensajeReject_Proposal(ACLMessage msg) {
                String contenido = msg.getContent();
                // Formato del mensaje recibido: "SubastaN: Has perdido la subasta con una puja de: X"
                double precioFinal = Double.parseDouble(contenido.split(": ")[2]);
                Subasta subasta = getSubasta(contenido.split(": ")[0]);

                if (subasta == null) {
                    throw new IllegalArgumentException("Subasta no encontrada");
                }

                if (subastaActual.equals(subasta.getNombre())) {
                    controller.setLabelEstadoText("PERDEDOR:"+precioFinal);
                    controller.añadirMensajeExterno("Perdedor!\n Puja final de: " + precioFinal);
                }

                controller.changeName(subasta.getNombre(), subasta.getNombre() + " (PERDIDA)");

                subasta.setEstado(Subasta.Estados.FINALIZADA);
                subasta.addMensajeExterno(msg);
                //doDelete();
            }

            /**
             * Gestiona los mensajes de tipo ACCEPT_PROPOSAL
             * Se dan cuando el vendedor acepta la puja del comprador. Es decir, este ha ganado
             * @param msg
             */
            private void gestionarMensjeAccept_Proposal(ACLMessage msg) {
                String contenido = msg.getContent();
                // Formato del mensaje recibido: "SubastaN: Has ganado la subasta con una puja de: X"
                double precioFinal = Double.parseDouble(contenido.split(": ")[2]);
                Subasta subasta = getSubasta(contenido.split(": ")[0]);

                if (subasta == null) {
                    throw new IllegalArgumentException("Subasta no encontrada");
                }

                if (subastaActual.equals(subasta.getNombre())) {
                    controller.setLabelEstadoText("GANADOR:"+precioFinal);
                    controller.añadirMensajeExterno("Ganador!\n Puja final de: " + precioFinal);
                    controller.setGanador(getLocalName());
                }
                controller.changeName(subasta.getNombre(), subasta.getNombre() + " (GANADA)");

                subasta.setEstado(Subasta.Estados.FINALIZADA);
                subasta.setGanador(getAID());
                subasta.addMensajeExterno(msg);

                ACLMessage transaccion = blockingReceive();
                controller.añadirMensajeExterno("Petición de transacción de\nvendedor: " + transaccion.getContent());

                doDelete();
            }

            /**
             * Gestiona los mensajes de tipo INFORM
             * Se dan cuando el vendedor informa de una nueva subasta
             * @param msg
             */
            private void gestionarMensajeInform(ACLMessage msg) {
                // Formato de mensaje recibido: "Subastas: , subasta1, "
                String contenido = msg.getContent();
                String[] partes = contenido.split(": ");
                String nombreSubasta = partes[0];
                // Distinguimos entre añadido de subasta(s): Subastas... o eliminado de subasta: Eliminar:... o Finalizar...
                if (nombreSubasta.equals("Subastas")) {
                    //Partes[1] a veces viene con corchetes, se eliminan
                    if (partes[1].startsWith("[")) {
                        partes[1] = partes[1].substring(1, partes[1].length() - 1);
                    }
                    String[] subastasM = partes[1].split(", ");
                    for (String subasta : subastasM) {
                        // Si no empieza por "Subasta" no es una subasta, se salta
                        if (!subasta.startsWith("Subasta")) continue;
                        subastas.put(new Subasta(subasta, 0, presupuestoMaximo), false); // Se sabe si está activa o no al suscribirse todo
                        controller.addSubastaNoSuscrita(subasta); //todo ver que mensaje manda el vendedor con "subasta"
                    }
                }
                // Si empieza por Finalizar:, es para avisarnos de una eliminación
                else if (partes[0].equals("Finalizar")) {
                    // Si no empieza por "Subasta" no es una subasta, se salta
                    nombreSubasta = partes[1];
                    if (!nombreSubasta.startsWith("Subasta")) return;
                    for (Subasta s : subastas.keySet()) {
                        if (s.getNombre().equals(nombreSubasta)) {
                            s.setEstado(Subasta.Estados.FINALIZADA);
                            controller.changeName(nombreSubasta, nombreSubasta + " (FINALIZADA)");
                            break;
                        }
                    }
                }

                else if (partes[0].equals("Eliminar")) { // todo revisar si fuciona pq ni idea
                    // Si no empieza por "Subasta" no es una subasta, se salta
                    nombreSubasta = partes[1];
                    if (!nombreSubasta.startsWith("Subasta")) return;
                    for (Subasta s : subastas.keySet()) {
                        if (s.getNombre().equals(nombreSubasta)) {
                            subastas.remove(s);
                            controller.eliminarSubasta(nombreSubasta);
                            break;
                        }
                    }
                }

            }

            /**
             * Gestiona los mensajes de tipo CFP
             * Se dan cuando el vendedor anuncia un precio en una subasta suscrita. Se decide si pujar (propose) o no
             * @param msg
             */
            private void gestionarMensajeCFP(ACLMessage msg) {
                // Formato de mensaje recibido: "SubastaN: Precio: X"
                if (!msg.getContent().startsWith("Subasta")) {
                    return;
                }

                // Subasta a la que se refiere el mensaje
                Subasta sub = null;

                for (Subasta s : subastas.keySet()) {
                    if (s.getNombre().equals(msg.getContent().split(": ")[0])) {
                        if (!subastas.get(s)) {
                            return;
                        }
                        else sub = s;
                    }
                }

                if (sub == null) {
                    return;
                }

                String contenido = msg.getContent();
                double precioActual = Double.parseDouble(contenido.split(": ")[2]);

                // Display por ventana del precio recibido
                sub.setPrecioActual(precioActual);
                sub.addMensajeExterno(msg);
                if (subastaActual.equals(sub.getNombre())) {
                    controller.añadirPrecio(String.valueOf(precioActual));
                    controller.actualizarPrecio(String.valueOf(precioActual));
                }
                // Decisión de puja
                if (precioActual <= presupuestoMaximo) { // Si está dentro del presupuesto, puja
                    pujar(msg, precioActual, sub);
                }
            }

            /**
             * Método que envía una puja al vendedor
             * @param msg
             * @param precioActual
             * @param subasta
             */
            private void pujar(ACLMessage msg, double precioActual, Subasta subasta) {
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                // Formato de mensaje: Subasta N: Puja: X
                reply.setContent(subasta.getNombre() + ": Puja: " + String.valueOf(precioActual));
                send(reply);

                subasta.addPuja(reply);
                if (subastaActual.equals(msg.getContent().split(": ")[0]))
                    controller.añadirPuja(String.valueOf(precioActual));
            }
        });


    }

    public void salidaDinamica() {
        ACLMessage msg = new ACLMessage(ACLMessage.CANCEL);
        msg.addReceiver(new jade.core.AID("Vendedor", jade.core.AID.ISLOCALNAME));
        msg.setContent("Salida dinámica");
        send(msg);
    }

    public void suscribirSubasta(String subasta) {
        Subasta sub = null;
        for (Subasta s : subastas.keySet()) {
            if (s.getNombre().equals(subasta)) {
                subastas.put(s, true);
                sub = s;
            }
        }
        if (sub == null) {
            System.out.println("Subasta no encontrada. ERROR AL SUSCRIBIR");
            throw new IllegalArgumentException("Subasta no encontrada");
        }

        // Enviamos mensaje al vendedor de que nos suscribimos a la subasta
        ACLMessage msg = new ACLMessage(ACLMessage.SUBSCRIBE);
        msg.addReceiver(new jade.core.AID("Vendedor", jade.core.AID.ISLOCALNAME));
        msg.setContent(subasta);
        send(msg);

        // Recibimos mensaje de confirmación de suscripción
        ACLMessage reply = blockingReceive();
        if (reply.getPerformative() == ACLMessage.CONFIRM) {
            // Formato del mensaje recibido: "CSubastaN: estado, precioActual"
            // Si no empieza por CSubastaN, SubastaN igual a la nuestra, salimos
            if (!reply.getContent().startsWith("C" + subasta)) {
                return;
            }

            if (reply.getContent().split(": ").length != 2) {
                System.out.println("Formato de mensaje incorrecto. ERROR AL SUSCRIBIR");
                throw new IllegalArgumentException("Formato de mensaje incorrecto");
            }
            reply.getContent();
            String[] partes = reply.getContent().split(": ");
            String estado = partes[1].split(", ")[0];
            String precioActual = partes[1].split(", ")[1];
            if (subasta.equals(subastaActual)) {
                controller.setLabelEstadoText(estado);
                controller.setLabelPrecioActual(precioActual);
                controller.setLabelSubastaSeleccionada(subasta);
            }
            sub.setEstado(Subasta.Estados.valueOf(estado));
            sub.actualizarPrecio(Double.parseDouble(precioActual));
        }

        System.out.println("Subasta suscrita con éxito: " + subasta);
    }
    public void desuscribirSubasta(String subasta) {
        Subasta sub = null;
        for (Subasta s : subastas.keySet()) {
            if (s.getNombre().equals(subasta)) {
                subastas.put(s, false);
                sub = s;
            }
        }
        if (sub == null) {
            throw new IllegalArgumentException("Subasta no encontrada");
        }

        // Enviamos mensaje al vendedor de que nos desuscribimos de la subasta
        ACLMessage msg = new ACLMessage(ACLMessage.CANCEL);
        msg.addReceiver(new jade.core.AID("Vendedor", jade.core.AID.ISLOCALNAME));
        msg.setContent(subasta);
        send(msg);

        if (subasta.equals(subastaActual)) {
            subastaActual = "";
            // Limpiamos los elementos que se muestran en la interfaz gráfica
            controller.setLabelEstadoText("");
            controller.setLabelPrecioActual("");
            controller.setLabelSubastaSeleccionada("");

            // Limpiamos los mensajes externos e internos
            controller.limpiarMensajesExternos();
            controller.limpiarMensajesInternos();
        }
    }
    public boolean isSuscrito(String subasta) {
        return subastas.get(subasta);
    }
    public void seleccionarSubasta(String subasta) {
        subastaActual = subasta;
    }
    public String getPrecioActual(String subasta) {
        for (Subasta s : subastas.keySet()) {
            if (s.getNombre().equals(subasta)) {
                return String.valueOf(s.getPrecioActual());
            }
        }
        return "";
    }

    public Subasta getSubasta(String subasta) {
        for (Subasta s : subastas.keySet()) {
            if (s.getNombre().equals(subasta)) {
                return s;
            }
        }
        return null;
    }

    public void setTope(double tope) {
        presupuestoMaximo = tope;
    }
}
