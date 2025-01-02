package com.mateobodenlle.englishauction;

import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Subasta {
    private String nombre;
    private double precioInicial;
    private double precioActual;
    private Set<AID> participantes = new HashSet<>();
    private ArrayList<ACLMessage> pujas = new ArrayList<>(); // Registro de pujas
    private boolean subastaActiva = false;
    private boolean pujaRecibida = true;
    private Estados estado = Estados.ACTIVA;
    private ArrayList<ACLMessage> mensajesExternos = new ArrayList<>();
    private AID ganador;


    public double getPrecioActual() {
        return precioActual;
    }

    public Set<AID> getCompradores() {
        return participantes;
    }

    public Collection<ACLMessage> getPujas() {
        return pujas;
    }

    public void actualizarPrecio(double incremento) {
        this.precioActual += incremento;
    }

    public void setEstado(Estados estado) {
        this.estado = estado;
    }

    public Estados getEstado() {
        return estado;
    }

    public ACLMessage[] getMensajesExternos() {
        return mensajesExternos.toArray(new ACLMessage[0]);
    }

    public void addMensajeExterno(ACLMessage msg) {
        mensajesExternos.add(msg);
    }

    public enum Estados {INACTIVA, ACTIVA, FINALIZADA, CANCELADA};
    private double tope = 0;

    /**
     * Constructor de la subasta para el vendedor
     * @param nombre
     * @param precioInicial
     */
    public Subasta(String nombre, double precioInicial) {
        this.estado = Estados.INACTIVA;
        this.mensajesExternos = new ArrayList();
        this.tope = 0.0;
        this.nombre = nombre;
        this.precioInicial = precioInicial;
        this.precioActual = precioInicial;

    }

    /**
     * Constructor de la subasta para el comprador
     * @param nombre Nombre de la subasta
     */
    public Subasta(String nombre, double precioActual, double tope) {
        this.estado = Estados.INACTIVA;
        this.mensajesExternos = new ArrayList();
        this.tope = 0.0;
        this.nombre = nombre;
        this.tope = tope;
        this.precioActual = precioActual;
    }

    public void setActivacion(boolean b) {
        this.subastaActiva = b;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean getPujaRecibida() {
        return pujaRecibida;
    }

    public void setPujaRecibida(boolean b) {
        this.pujaRecibida = b;
    }

    @Override //equals
    public boolean equals(Object obj) {
        if (obj instanceof Subasta) {
            Subasta s = (Subasta) obj;
            return s.getNombre().equals(this.getNombre());
        }
        return false;
    }

    public double getTope() {
        return tope;
    }

    public void setTope(double tope) {
        this.tope = tope;
    }

    public void addParticipante(AID aid) {
        participantes.add(aid);
    }

    public void addPuja(ACLMessage msg) {
        pujas.add(msg);
    }

    public void finalizar() {
        estado = Estados.FINALIZADA;
    }

    public void cancelar() {
        estado = Estados.CANCELADA;
    }

    public void setPrecioActual(double precio) {
        precioActual = precio;
    }

    public double getPrecioInicial() {
        return precioInicial;
    }

    public void setPrecioInicial(double precio) {
        precioInicial = precio;
    }

    public void setParticipantes(Set<AID> participantes) {
        this.participantes = participantes;
    }

    public void setPujas(ArrayList<ACLMessage> pujas) {
        this.pujas = pujas;
    }

    public void setSubastaActiva(boolean subastaActiva) {
        this.subastaActiva = subastaActiva;
    }

    public AID getGanador() {
        return ganador;
    }

    public void setGanador(AID ganador) {
        this.ganador = ganador;
    }
}
