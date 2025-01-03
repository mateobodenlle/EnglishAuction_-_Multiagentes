package com.mateobodenlle.seller;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.introspection.SuspendedAgent;
import jade.lang.acl.ACLMessage;

import java.lang.reflect.Array;
import java.util.*;

// Importamos la clase subasta compartida entre seller y buyer
import com.mateobodenlle.englishauction.Subasta;


public class SellerAgent extends Agent {
    private ArrayList<ACLMessage> colaMensajes = new ArrayList<>();
    private double precioActual = 20.0; // todo remove
    private double incremento = 10.0;
    private Set<AID> compradoresRegistrados = new HashSet<>();
    /**
     * Lista de subastas del vendedor. El resto de la información encapsulada en cada subasta.
     */
    private ArrayList<Subasta> subastas = new ArrayList<>();
    /**
     * Subasta seleccionada, usada para gestionar gráficamente las subastas. Solo importa para el front.
     */
    private Subasta subastaSeleccionada;

    private SellerController controller;



    // Llamado desde el hilo de FX
    public void iniciarSubasta() {
        subastaSeleccionada.setActivacion(true);
        subastaSeleccionada.setEstado(Subasta.Estados.ACTIVA);
        controller.actualizarPrecio(String.valueOf(precioActual));
    }

    @Override
    protected void setup() {
        System.out.println("Agente Vendedor iniciado: " + getAID().getName());

        // Controlador FX
        controller = SellerController.getInstance();
        controller.setSellerAgent(this);

        /**
         * Comportamiento para enviar precios y gestionar pujas
         */
        addBehaviour(new TickerBehaviour(this, 2000) { // Espera de 10 segundos entre ejecuciones
            @Override
            protected void onTick() {
                //Iteramos sobre una copia de las subastas, para no incurrir en modificación concurrente
                ArrayList<Subasta> subastasCopy = new ArrayList<>(SellerAgent.this.subastas);
                for (Subasta subasta : subastasCopy) {
                    System.out.println("Gestionando subasta: " + subasta.getNombre());
                    // Esperamos a que se pulse el botón de empezar subasta
                    if (!subasta.getEstado().equals(Subasta.Estados.ACTIVA)) {
                        System.out.println("Subasta no activa: " + subasta.getNombre());
                        continue;
                    }
                    System.out.println("Subasta activa: " + subasta.getNombre());

                    // Comprobamos si el precio anterior ha tenido pujas
                    if (!subasta.getPujaRecibida()) {
                        finalizar(subasta); // todo rework
                        System.out.println("Subasta finalizada: " + subasta.getNombre());
                        continue;
                    }
                    System.out.println("Subasta con pujas o primera vuelta: " + subasta.getNombre());

                    // Actualizamos la interfaz gráfica si la subasta está seleccionada
                    if (subasta.equals(subastaSeleccionada))
                        controller.actualizarPrecio(String.valueOf(subasta.getPrecioActual()));

                    // Enviamos el precio a los compradores
                    envioPrecio(subasta);

                    // Marcamos preeliminarmente que no se han recibido pujas
                    subasta.setPujaRecibida(false);
                    System.out.println("Marcando subasta como no recibida: " + subasta.getNombre());

                    // Esperamos por la respuesta
//                    block(50);
                    recibirMensajesPendiente();


                    // Recorremos la cola de mensajes, procesando los relativos a esta subasta
                    System.out.println("Procesando cola de mensajes para subasta: " + subasta.getNombre());
                    procesarColaMensajes(subasta);

                    // Actualizamos el precio actual
                    subasta.actualizarPrecio(incremento);
                }
                // Actualizamos los cambios los elementos de subastasCopy en subastas
                for (Subasta s : subastasCopy) {
                    for (Subasta s2 : subastas) {
                        if (s.equals(s2)) {
                            s2 = s; // todo revisar
                            break;
                        }
                    }
                }
            }

            /**
             * Gestionar mensajes en cola del agente
             */
            private void recibirMensajesPendiente() {
//                block(25);
                while (true){
                    ACLMessage msg = receive();
                    if (msg != null) {
                        System.out.println("Mensaje recibido: " + msg.getContent());
                        // Si es un mensaje de registro global
                        if (msg.getPerformative() == ACLMessage.INFORM) {
                            registroGlobal(msg);
                        }
                        else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                            // Si el comprador envía una puja
                            colaMensajes.add(msg);
                            System.out.println("Añadiendo puja a cola: " + msg.getContent());
                        }
                        else if (msg.getPerformative() == ACLMessage.SUBSCRIBE) { // Si es una subscripción
                            System.out.println("Subscripción a subasta: " + msg.getContent());
                            suscripcionSubasta(msg);
                        } else if (msg.getPerformative() == ACLMessage.CANCEL) { // Si es una cancelación
                            desuscripcionSubasta(msg);

                        }

                    } else {
                        return;
                    }
                }
            }

            // Funciones de gestión de mensajes recibidos


            private void desuscripcionSubasta(ACLMessage msg) {
                // Buscamos la subasta y eliminamos al comprador
                for (Subasta s : subastas) {
                    if (s.getNombre().equals(msg.getContent())) {
                        s.getCompradores().remove(msg.getSender());
                        // Si está seleccionada actualizamos la lista del controlador
                        if (s.equals(subastaSeleccionada)) {
                            controller.setCompradoresSubasta(s);
                        }
                        break;
                    }
                }

            }

            private void suscripcionSubasta(ACLMessage msg) {
                System.out.println("Subscripción a subasta: " + msg.getContent());
                Subasta subasta = null;
                for (Subasta s : subastas) {
                    if (s.getNombre().equals(msg.getContent())) {
                        subasta = s;
                        System.out.println("Subasta encontrada: " + s.getNombre());
                        break;
                    }
                }
                if (subasta == null) {
                    System.out.println("Subasta no encontrada: ERRIR AL SUSCRIBIR. " + msg.getContent());
                    return;
                }
                if (subasta != null) {
                    subasta.getCompradores().add(msg.getSender());
                    System.out.println("Comprador añadido a subasta: " + msg.getContent());
                    // Si está seleccionada actualizamos la lista del controlador
                    if (subasta.equals(subastaSeleccionada))
                        controller.setCompradoresSubasta(subasta);
                    // Mandamos info al comprador. Formato del mensaje: "CSubastaN: estado, precioActual"
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.CONFIRM);
                    reply.setContent("C" + subasta.getNombre() + ": " + subasta.getEstado() + ", " + subasta.getPrecioActual());
                    send(reply);
                }
            }

            private void registroGlobal(ACLMessage msg) {
                AID nuevoComprador = msg.getSender();
                System.out.println("Nuevo comprador registrado: " + nuevoComprador.getLocalName());
                compradoresRegistrados.add(nuevoComprador);
                controller.añadirComprador(nuevoComprador.getLocalName());

                // Le enviamos la lista de subastas todo gestionar recibo de subastas
                ACLMessage subastas = new ACLMessage(ACLMessage.INFORM);
                subastas.setContent("Subastas: " + Arrays.toString(getSubastas()));
                subastas.addReceiver(nuevoComprador);
                send(subastas);
            }


            // Funciones de gestión de subastas

            private void procesarColaMensajes(Subasta subasta) {
                for (ACLMessage mensaje : colaMensajes) {
                    // Mensajes con formato "Subasta N: Puja: X"
                    if (mensaje.getPerformative() == ACLMessage.PROPOSE && mensaje.getContent().split(":")[0].equals(subasta.getNombre())) {
                        String contenido = mensaje.getContent();
                        System.out.println("Procesando mensaje: " + contenido);
                        if (contenido.split(":")[1].equals("Puja")) {
                            double puja = Double.parseDouble(contenido.split(": ")[2]);
                            System.out.println("Mensaje de puja: " + puja);

                            // Guardamos la puja
                            subasta.getPujas().add(mensaje);

                            // Actualizamos la interfaz gráfica si la subasta está seleccionada
                            if (subasta.equals(subastaSeleccionada))
                                controller.añadirPuja(mensaje.getSender().getLocalName(), puja);

                            subasta.setPujaRecibida(true);
                            colaMensajes.remove(mensaje);
                        }
                        else {
                            // Not understood
                            ACLMessage reply = mensaje.createReply();
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            send(reply);
                        }
                    }
                }
            }

            private void recibirYGestionarPujas(Subasta subasta) {// todo remove
                // Esperamos las respuestas de los compradores
                ACLMessage respuesta = null;
                for (AID _ : subasta.getCompradores()){
                    respuesta = blockingReceive(50);
                    if (respuesta == null) {
                        System.out.println("No se recibió respuesta de compradores para esta subasta.");
                        return;
                    }

                    if (respuesta.getPerformative() == ACLMessage.PROPOSE){
                        String contenido = respuesta.getContent();
                        if (contenido.split(":")[0].equals("Puja")) {
                            double puja = Double.parseDouble(contenido.split(": ")[1]);

                            // Guardamos la puja
                            subasta.getPujas().add(respuesta);

                            // Actualizamos la interfaz gráfica si la subasta está seleccionada
                            if (subasta.equals(subastaSeleccionada))
                                controller.añadirPuja(respuesta.getSender().getLocalName(), puja);

                            subasta.setPujaRecibida(true);
                        }
                        else {
                            // Not understood
                            ACLMessage reply = respuesta.createReply();
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            send(reply);
                        }
                    }
            }
            }

            private void envioPrecio(Subasta subasta) {
                // Enviamos CFP del precio a los compradores registrados
                ACLMessage cfpPrecio = new ACLMessage(ACLMessage.CFP);
                // Mensajes con formato "SubastaN: Precio: X"
                cfpPrecio.setContent(subasta.getNombre()+ ": Precio: " + subasta.getPrecioActual());
                for (AID comprador : subasta.getCompradores()) {
                    cfpPrecio.addReceiver(comprador);
                }
                send(cfpPrecio);
            }

            /**
             * Busca el ganador de la subasta y avisa al ganador y a los perdedores.
             * Inicia la transacción de compra
             * @param subasta
             */
            private void finalizar(Subasta subasta) {
                ACLMessage pujaGanadora = null;
                // Actualizamos la interfaz gráfica si la subasta está seleccionada
                if (subasta.equals(subastaSeleccionada))
                    controller.añadirPuja("No hay ninguna puja a: ", subasta.getPrecioActual()-incremento);

                // Buscamos la puja ganadora (primero con el último precio con pujas)
                AID ganador = null;
                for (ACLMessage propuesta : subasta.getPujas()) {
                    // Mensajes con formato "SubastaN: Puja: X"
                    double precioPropuesta = Double.parseDouble(propuesta.getContent().split(": ")[2]);

                    // Comprobamos si la puja es la ganadora (primer puja a máximo precio) y si el comprador está registrado en la subasta
                    if (precioPropuesta == (subasta.getPrecioActual()-2*incremento) && (new ArrayList<>(List.of(subasta.getCompradores()))).contains(propuesta.getSender())) {
                        ganador = propuesta.getSender();
                        pujaGanadora = propuesta;
                        break;
                    }
                }
                // Notificamos a los perdedores
                for (ACLMessage propuesta : subasta.getPujas()) {
                    if (!propuesta.equals(pujaGanadora) && !propuesta.getSender().equals(ganador) && (new ArrayList<>(List.of(subasta.getCompradores()))).contains(propuesta.getSender())) {
                        notificarPerdedor(subasta, propuesta);
                    }
                }

                // Si hay ganador, notificamos y comenzamos la transacción
                if (ganador != null) {
                    // Actualizamos la interfaz gráfica si la subasta está seleccionada
                    if (subasta.equals(subastaSeleccionada))
                        controller.añadirPuja("El ganador es " + ganador.getLocalName() + " con una puja de ", subasta.getPrecioActual()-2*incremento);

                    // Notificamos al ganador
                    subasta.setGanador(ganador);
                    notificarGanador(pujaGanadora, subasta.getPrecioActual()-2*incremento, subasta);
                    iniciarTransaccion(pujaGanadora, subasta);
                } else {
                    // Si falla algo, no hay ganador
                    System.out.println("No hubo ganador.");
                }

                // Actualizamos la interfaz gráfica si la subasta está seleccionada
                if (subasta.equals(subastaSeleccionada))
                    controller.precioFinal(String.valueOf(subasta.getPrecioActual()-2*incremento));

                subasta.setEstado(Subasta.Estados.FINALIZADA);
                //eliminarSubasta(subasta);
                estadoFinalizadoSubasta(subasta);
            }

            private void notificarPerdedor(Subasta subasta, ACLMessage propuesta) {
                ACLMessage respuesta = propuesta.createReply();
                respuesta.setPerformative(ACLMessage.REJECT_PROPOSAL);
                // Formato del mensaje: "SubastaN: Has perdido la subasta con una puja de: X"
                respuesta.setContent(subasta.getNombre()+": Has perdido la subasta con una puja de: " + Double.parseDouble(propuesta.getContent().split(": ")[2]));
                send(respuesta);
            }

            private void notificarGanador(ACLMessage pujaGanadora, double v, Subasta subasta) {
                ACLMessage respuesta = pujaGanadora.createReply();
                respuesta.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                // Formato del mensaje: "SubastaN: Has ganado la subasta con una puja de: X"
                respuesta.setContent(subasta.getNombre() + ": Has ganado la subasta con una puja de: " + v);
                send(respuesta);
            }

            private void iniciarTransaccion(ACLMessage pujaGanadora, Subasta subasta) {
                // Iniciar transacción
                ACLMessage transaccion = new ACLMessage(ACLMessage.REQUEST);
                transaccion.setContent("Transacción de\n" + pujaGanadora.getSender().getLocalName() + " por " + (subasta.getPrecioActual()-incremento));
                transaccion.addReceiver(pujaGanadora.getSender());
                send(transaccion);
            }
        });


    }

    public void gestionarSubasta(String selectedItem) {
        // Buscamos la subasta seleccionada
        for (Subasta subasta : subastas) {
            if (subasta.getNombre().equals(selectedItem)) {
                setSubastaSeleccionada(subasta);
                break;
            }
        }

        controller.labelPrecioActual.setText("Precio: " + subastaSeleccionada.getPrecioActual());
        controller.labelEstado.setText("Estado: " + subastaSeleccionada.getEstado());
        controller.setCompradoresSubasta(subastaSeleccionada);
        controller.setPujasSubasta(subastaSeleccionada);
    }

    public String nuevaSubasta() {
        // Creamos una nueva subasta
        String nombre = "Subasta " + subastas.size();
        Subasta subasta = new Subasta(nombre, precioActual);
        subastas.add(subasta);

        // Avisamos a los compradores de la nueva subasta
        avisoNuevaSubasta(subasta);

        // Devolvemos el nombre para añadirlo a la lista en front
        return nombre;
    }

    private void avisoNuevaSubasta(Subasta subasta) {
        // Enviamos un mensaje a los compradores registrados
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        // Formato de mensaje "Subastas: nombreSubasta"
        inform.setContent("Subastas: " + subasta.getNombre());
        for (AID comprador : compradoresRegistrados) {
            inform.addReceiver(comprador);
        }
        send(inform);
        System.out.println("Subastas: " + subasta.getNombre());

    }

    public Subasta[] getSubastas() { //todo revisar
        return subastas.toArray(new Subasta[0]);
    }

    public void eliminarSubasta(String selectedItem) {
        // Eliminamos la subasta cuyo nombre coincida con el seleccionado
        for (Subasta subasta : subastas) {
            if (subasta.getNombre().equals(selectedItem)) {
                eliminarSubasta(subasta);
                break;
            }
        }
    }
    public void eliminarSubasta(Subasta subasta){
        controller.listSubastas.getItems().remove(subasta.getNombre());
        // Avisamos a los compradores de la eliminación de la subasta
        avisoEliminarSubasta(subasta);
        subastas.remove(subasta);
    }

    private void estadoFinalizadoSubasta(Subasta subasta) {
        // Cambiamos el nombre de la subasta para indicar que ha finalizado
        System.out.println("Cambiando nombre de subasta: " + subasta.getNombre());
        controller.changeName(subasta.getNombre(), subasta.getNombre() + " (FINALIZADA)");
        avisoFinalizarSubasta(subasta);

    }

    private void avisoEliminarSubasta(Subasta subasta) {
        // Enviamos un mensaje a los compradores registrados
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        // Formato de mensaje "Eliminar: nombreSubasta"
        inform.setContent("Eliminar: " + subasta.getNombre());
        for (AID comprador : compradoresRegistrados) {
            inform.addReceiver(comprador);
        }
        send(inform);
        System.out.println("Eliminar: " + subasta.getNombre());
    }

    private void avisoFinalizarSubasta(Subasta subasta) {
        // Enviamos un mensaje a los compradores registrados
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        // Formato de mensaje "Finalizar: nombreSubasta"
        inform.setContent("Finalizar: " + subasta.getNombre());
        for (AID comprador : compradoresRegistrados) {
            // Si está suscrito, ya se le ha avisado (perdedor/ganador)
            if (!subasta.getCompradores().contains(comprador))
                inform.addReceiver(comprador);
        }
        send(inform);
        System.out.println("Finalizar: " + subasta.getNombre());
    }

    /**
     * Método para cierre de subasta; avisar a compradores interesados.
     * @param subasta
     */
    private void terminarSubasta(Subasta subasta) { // todo
    }

    public Subasta getSubastaSeleccionada() {
        return subastaSeleccionada;
    }

    public void setSubastaSeleccionada(Subasta subastaSeleccionada) {
        this.subastaSeleccionada = subastaSeleccionada;
    }
}
