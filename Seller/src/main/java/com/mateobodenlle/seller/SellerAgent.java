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
        controller.actualizarPrecio(String.valueOf(subastaSeleccionada.getPrecioInicial()));
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
                    if (subasta.getPujaRecibida() < 2) {
                        finalizar(subasta);
                        continue;
                    }

                    // Actualizamos la interfaz gráfica si la subasta está seleccionada
                    if (subasta.equals(subastaSeleccionada))
                        controller.actualizarPrecio(String.valueOf(subasta.getPrecioActual()));

                    // Enviamos el precio a los compradores suscritos
                    envioPrecio(subasta);

                    // Marcamos preliminarmente que no se han recibido pujas
                    subasta.setPujaRecibida(0);

                    recibirMensajesPendiente();

                    // Recorremos la cola de mensajes, procesando los relativos a esta subasta
                    procesarColaPujas(subasta);

                    // Actualizamos el precio actual
                    subasta.actualizarPrecio(subasta.getIncremento());
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
                Subasta subasta = null;
                for (Subasta s : subastas) {
                    if (s.getNombre().equals(msg.getContent())) {
                        subasta = s;
                        break;
                    }
                }
                if (subasta == null) {
                    return;
                }
                else {
                    subasta.getCompradores().add(msg.getSender());
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

                // Le enviamos la lista de subastas
                ACLMessage Msubastas = new ACLMessage(ACLMessage.INFORM);
                // Formato de mensaje "Subastas: [nombreSubasta, nombreSubasta2...]"
                String stringSubastas = "[";
                for (Subasta subasta : subastas) {
                    stringSubastas += subasta.getNombre() + ", ";
                }

                Msubastas.setContent("Subastas: " + stringSubastas + "]");
                Msubastas.addReceiver(nuevoComprador);
                send(Msubastas);
            }


            // Funciones de gestión de subastas

            /**
             * Procesa los mensajes de la cola de mensajes que corresponden a la subasta. Solo debería haber pujas.
             * Acepta la primera puja a un precio, rechaza las demás.
             * Actualiza la interfaz gráfica.
             * @param subasta
             */
            private void procesarColaPujas(Subasta subasta) {
                Boolean primero = true;
                ArrayList<ACLMessage> consumidos = new ArrayList<>(); // Mensajes consumidos, para eliminar
                for (ACLMessage mensaje : colaMensajes) {
                    // Mensajes con formato "Subasta N: Puja: X"
                    // Comprobamos si el mensaje es una puja y si es de la subasta actual
                    if (mensaje.getPerformative() == ACLMessage.PROPOSE && mensaje.getContent().split(":")[0].equals(subasta.getNombre())) {
                        String contenido = mensaje.getContent();

                        // Si el mensaje es una puja
                        if (contenido.split(":")[1].equals(" Puja")) {
                            double puja = Double.parseDouble(contenido.split(": ")[2]);

                            // Guardamos la puja
                            subasta.getPujas().add(mensaje);

                            // Si es la primera puja a ese precio en esa subasta; aceptamos
                            if (primero) {
                                primero = false;
                                // Reply a mensaje
                                ACLMessage reply = mensaje.createReply();
                                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                reply.setContent(subasta.getNombre()+": ACCEPT: " + puja);
                                send(reply);
                            }
                            //Si no, rechazamos
                            else {
                                ACLMessage reply = mensaje.createReply();
                                reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                                reply.setContent(subasta.getNombre()+": REJECT: " + puja);
                                send(reply);
                            }


                            // Actualizamos la interfaz gráfica si la subasta está seleccionada
                            if (subasta.equals(subastaSeleccionada))
                                controller.añadirPuja(mensaje.getSender().getLocalName(), puja);

                            subasta.setPujaRecibida(subasta.getPujaRecibida()+1);
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


            /**
             * Envía el precio actual a los compradores suscritos a la subasta
             * @param subasta
             */
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
            private void finalizar(Subasta subasta){ // todo comprbar tras rework
                // Avisamos que no hay pujas a este precio
                if (subasta.getPujaRecibida() == 0)
                    controller.añadirPuja("No hay ninguna puja a: ", subasta.getPrecioActual()-subasta.getIncremento());
                else
                    controller.añadirPuja("Puja final a: ", subasta.getPrecioActual()-subasta.getIncremento());

                // Actualizamos el estado de la subasta
                subasta.setEstado(Subasta.Estados.FINALIZADA);
                Double precioVictoria = subasta.getPrecioActual()-(2-subasta.getPujaRecibida())*subasta.getIncremento();
                subasta.setPrecioActual(precioVictoria);

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
                // Notificamos
                notificarResultado(subasta, pujaGanadora, ganador);

                // Iniciamos la transacción
                if (pujaGanadora != null)
                    iniciarTransaccion(pujaGanadora, subasta);

                // Actualizamos gráfico
                if (subasta.equals(subastaSeleccionada)) {
                    controller.precioFinal(String.valueOf(precioVictoria));
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

                    if (precioPropuesta == subasta.getPrecioActual()){
                        // Cuando un comprador se desuscribe NO se anulan sus pujas.
                        pujaGanadora = propuesta;
                        break;
                    }
                }
                return pujaGanadora;
            }


            private void notificarPerdedor(Subasta subasta, ACLMessage pujaGanadora, AID comprador) {
                ACLMessage respuesta = new ACLMessage(ACLMessage.INFORM);
                respuesta.addReceiver(comprador);
                // Formato del mensaje: "PERDER: SubastaN: Has perdido la subasta.
                respuesta.setContent("PERDER: "+subasta.getNombre()+": Has perdido la subasta. Precio final de: " + subasta.getPrecioActual());
                send(respuesta);
            }

            private void notificarGanador(Subasta subasta, ACLMessage pujaGanadora) {
                //Mensaje inform de ganador
                ACLMessage notifGanador = new ACLMessage(ACLMessage.INFORM);
                notifGanador.addReceiver(pujaGanadora.getSender());

                // Formato del mensaje: "GANAR: SubastaN: Has ganado la subasta con una puja de: X"
                notifGanador.setContent("GANAR: "+subasta.getNombre() + ": Has ganado la subasta con una puja de: " + subasta.getPrecioActual());
                send(notifGanador);
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
                transaccion.setContent("Transaccion de\n" + pujaGanadora.getSender().getLocalName() + " por " + (subasta.getPrecioActual()-subasta.getIncremento()));
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
        Subasta subasta = new Subasta(nombre, 80.0);
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

    public ArrayList<Subasta> getSubastas() { //todo revisar
        return subastas;
    }

    public void setPrecioInicial(double v) {
        try {
            subastaSeleccionada.setPrecioInicial(v);
            subastaSeleccionada.setPrecioActual(v);
        } catch (Exception e) {
            System.out.println("Error al actualizar precio inicial");
        }
    }

    public void setPaso(String selectedItem, double v) {
        try {
            subastaSeleccionada.setIncremento(v);
        }
        catch (Exception e) {
            System.out.println("Error al actualizar paso");
        }
    }
}
