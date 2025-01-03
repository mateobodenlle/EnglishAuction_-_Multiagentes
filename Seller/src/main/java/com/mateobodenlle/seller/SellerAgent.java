package com.mateobodenlle.seller;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

// Importamos la clase subasta compartida entre seller y buyer
import com.mateobodenlle.englishauction.Subasta;
import javafx.application.Platform;


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
                recibirMensajesPendiente();
                //Iteramos sobre una copia de las subastas, para no incurrir en modificación concurrente
                for (Subasta subasta : subastas) {
                    // Esperamos a que se pulse el botón de empezar subasta
                    if (!subasta.getEstado().equals(Subasta.Estados.ACTIVA)) {
                        System.out.println("Subasta no activa: " + subasta.getNombre());
                        continue;
                    }

                    // Comprobamos si el precio anterior ha tenido pujas
                    if (!subasta.getPujaRecibida()) {
                        finalizar(subasta); // todo rework
                        continue;
                    }

                    // Actualizamos la interfaz gráfica si la subasta está seleccionada
                    if (subasta.equals(subastaSeleccionada))
                        controller.actualizarPrecio(String.valueOf(subasta.getPrecioActual()));

                    // Enviamos el precio a los compradores
                    envioPrecio(subasta);

                    // Marcamos preeliminarmente que no se han recibido pujas
                    subasta.setPujaRecibida(false);

                    recibirMensajesPendiente();

                    // Recorremos la cola de mensajes, procesando los relativos a esta subasta
                    procesarColaMensajes(subasta);

                    // Actualizamos el precio actual
                    subasta.actualizarPrecio(incremento);
                }
            }

            /**
             * Gestionar mensajes en cola del agente
             */
            private void recibirMensajesPendiente() {
                while (true){
                    ACLMessage msg = blockingReceive(25);
                    if (msg != null) {
                        // Si es un mensaje de registro global
                        if (msg.getPerformative() == ACLMessage.INFORM)
                            registroGlobal(msg);

                        else if (msg.getPerformative() == ACLMessage.PROPOSE)
                            // Si el comprador envía una puja
                            colaMensajes.add(msg);

                        else if (msg.getPerformative() == ACLMessage.SUBSCRIBE)  // Si es una subscripción
                            suscripcionSubasta(msg);

                        else if (msg.getPerformative() == ACLMessage.CANCEL)  // Si es una cancelación
                            desuscripcionSubasta(msg);

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
                ArrayList<ACLMessage> consumidos = new ArrayList<>();
                for (ACLMessage mensaje : colaMensajes) {
                    // Mensajes con formato "Subasta N: Puja: X"
                    if (mensaje.getPerformative() == ACLMessage.PROPOSE && mensaje.getContent().split(":")[0].equals(subasta.getNombre())) {
                        String contenido = mensaje.getContent();
                        if (contenido.split(":")[1].equals(" Puja")) {
                            double puja = Double.parseDouble(contenido.split(": ")[2]);

                            // Guardamos la puja
                            subasta.getPujas().add(mensaje);

                            // Actualizamos la interfaz gráfica si la subasta está seleccionada
                            if (subasta.equals(subastaSeleccionada))
                                controller.añadirPuja(mensaje.getSender().getLocalName(), puja);

                            subasta.setPujaRecibida(true);
                            consumidos.add(mensaje);
                        }
                        else {
                            // Not understood
                            ACLMessage reply = mensaje.createReply();
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            send(reply);
                        }
                    }
                }
                colaMensajes.removeAll(consumidos);
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
             * Actualiza el controlador gráfico
             * Busca al ganador y a la puja ganadora y notifica
             * Notifica a perdedores
             * Y notifica a los demás de que se ha acabado la subasta
             * Inicia la transacción de compra
             * @param subasta
             */
            private void finalizar(Subasta subasta){
                // Avisamos que no hay pujas a este precio
                controller.añadirPuja("No hay ninguna puja a: ", subasta.getPrecioActual()-incremento);

                // Actualizamos el estado de la subasta
                subasta.setEstado(Subasta.Estados.FINALIZADA);
                // Buscamos la puja ganadora (primero con el último precio con pujas)
                ACLMessage pujaGanadora = findPujaGanadora(subasta);
                AID ganador;
                if (pujaGanadora != null) {
                    ganador = pujaGanadora.getSender();

                // Actualizamos datos de la subasta. Ganador, precio...
                    subasta.setGanador(ganador);
                } else {
                    ganador = null;
                }
                subasta.setPrecioActual(subasta.getPrecioActual()-incremento);

                // Notificamos
                notificarResultado(subasta, pujaGanadora, ganador);

                // Iniciamos la transacción
                if (pujaGanadora != null)
                    iniciarTransaccion(pujaGanadora, subasta);

                // Actualizamos gráfico
                if (subasta.equals(subastaSeleccionada)) {
                    controller.precioFinal(String.valueOf(subasta.getPrecioActual() - incremento));
                    Platform.runLater(() -> controller.labelEstado.setText("FINALIZADA"));
                    if (ganador != null)
                        Platform.runLater(() -> controller.labelGanador.setText("Ganador: " + ganador.getLocalName()));
                }
                controller.changeName(subasta.getNombre(), subasta.getNombre() + " (FINALIZADA)");

            }

            private void notificarResultado(Subasta subasta, ACLMessage pujaGanadora, AID ganador) {
                for (AID comprador: compradoresRegistrados){
                    if (comprador.equals(ganador))
                        notificarGanador(subasta, pujaGanadora);
                    else if (subasta.getCompradores().contains(comprador))
                        notificarPerdedor(subasta, pujaGanadora, comprador);
                    else
                        notificarFinal(subasta, comprador);
                }
            }

            private ACLMessage findPujaGanadora(Subasta subasta) {
                ACLMessage pujaGanadora = null;
                for (ACLMessage propuesta : subasta.getPujas()) {
                    // Mensajes con formato "SubastaN: Puja: X"
                    double precioPropuesta = Double.parseDouble(propuesta.getContent().split(": ")[2]);
                    // Comprobamos si la puja es la ganadora (primer puja a máximo precio) y si el comprador está registrado en la subasta

                    if (precioPropuesta == (subasta.getPrecioActual()-2*incremento)) {
                        System.out.println("PUJA GANADORA: " + propuesta.getContent());
                        // Cuando un comprador se desuscribe NO se anulan sus pujas.
                        pujaGanadora = propuesta;
                        break;
                    }
                }
                return pujaGanadora;
            }


            private void notificarPerdedor(Subasta subasta, ACLMessage pujaGanadora, AID comprador) {
                for (ACLMessage propuesta : subasta.getPujas()) {
                    if (propuesta.getSender().equals(comprador) && !propuesta.equals(pujaGanadora)) {
                        ACLMessage respuesta = propuesta.createReply();
                        respuesta.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        // Formato del mensaje: "SubastaN: Has perdido la subasta con una puja de: X"
                        respuesta.setContent(subasta.getNombre()+": Has perdido la subasta con una puja de: " + Double.parseDouble(propuesta.getContent().split(": ")[2]));
                        send(respuesta);
                    }
                }
            }

            private void notificarGanador(Subasta subasta, ACLMessage pujaGanadora) {
                // Extraemos precio
                double v = Double.parseDouble(pujaGanadora.getContent().split(": ")[2]);

                ACLMessage respuesta = pujaGanadora.createReply();
                respuesta.setPerformative(ACLMessage.ACCEPT_PROPOSAL);

                // Formato del mensaje: "SubastaN: Has ganado la subasta con una puja de: X"
                respuesta.setContent(subasta.getNombre() + ": Has ganado la subasta con una puja de: " + v);
                send(respuesta);
            }

            /**
             * Método para avisar a AID comprador de que la subasta ha finalizado
             * Pensado para compradores NO suscritos
             * @param subasta
             * @param comprador
             */
            private void notificarFinal(Subasta subasta, AID comprador) {
                // Enviamos un mensaje a los compradores registrados
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                // Formato de mensaje "Finalizar: nombreSubasta"
                inform.setContent("Finalizar: " + subasta.getNombre());
                inform.addReceiver(comprador);
                send(inform);
                System.out.println("Finalizar: " + subasta.getNombre());
            }

            private void iniciarTransaccion(ACLMessage pujaGanadora, Subasta subasta) {
                // Iniciar transacción
                ACLMessage transaccion = new ACLMessage(ACLMessage.REQUEST);
                transaccion.setContent("Transaccion de\n" + pujaGanadora.getSender().getLocalName() + " por " + (subasta.getPrecioActual()-incremento));
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


    public Subasta getSubastaSeleccionada() {
        return subastaSeleccionada;
    }

    public void setSubastaSeleccionada(Subasta subastaSeleccionada) {
        this.subastaSeleccionada = subastaSeleccionada;
    }

    public Subasta[] getSubastas() { //todo revisar
        return subastas.toArray(new Subasta[0]);
    }
}
