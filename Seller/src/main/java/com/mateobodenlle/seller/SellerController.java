package com.mateobodenlle.seller;

import com.mateobodenlle.englishauction.Subasta;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

// Importamos la clase subasta


import java.io.IOException;

public class SellerController {
    private static SellerController instance;
    private Stage stage;
    private SellerAgent sellerAgent;
    @FXML
    public Label labelPrecioActual;
    @FXML
    public Button buttonEmpezarSubasta;
    @FXML
    public ListView<String> listCompradoresGlobal;
    @FXML
    public ListView<String> listCompradoresSubasta;
    @FXML
    public VBox vBoxPujas;
    @FXML
    public com.gluonhq.charm.glisten.control.TextField textFieldPrecioInicial;
    @FXML
    public Label labelEstado;
    @FXML
    public Label labelGanador;
    // Gestionar multisubastas
    @FXML
    public ListView<String> listSubastas;
    @FXML
    public Button buttonNuevaSubasta;
    @FXML
    public Button buttonEliminarSubasta;
    @FXML
    public Button buttonGestionar;


    /**
     * Método que se ejecuta al pulsar el botón "Empezar subasta". Debe llamar al inicio de subasta en el agente
     * y actualizar los elementos gráficos necesarios.
     */
    @FXML
    protected void onButtonEmpezarSubastaClick() {
        labelPrecioActual.setText("Cargando precio actual...");
        sellerAgent.iniciarSubasta();
        buttonEmpezarSubasta.setDisable(true);
    }

    /**
     * Método que se ejecuta al pulsar el botón "Gestionar". Selecciona la subasta seleccionada de la lista para
     * gestionarla. Solo son cambios gráficos, ya que lo que estamos cambiando es a qué subasta se refieren los inputs
     * y outputs de la ventana. El agente trabaja simultáneamente con todas las subastas.
     */
    @FXML
    protected void onButtonGestionarClick() {
        sellerAgent.gestionarSubasta(listSubastas.getSelectionModel().getSelectedItem());
        Platform.runLater(() -> buttonEmpezarSubasta.setDisable(false));
        Platform.runLater(() -> textFieldPrecioInicial.setDisable(false));
    }

    /**
     * Método que se ejecuta al pulsar el botón "Nueva subasta". Crea una nueva subasta en el agente y la añade a la lista
     */
    @FXML
    protected void onButtonNuevaSubastaClick() {
        String nombre = sellerAgent.nuevaSubasta();
        listSubastas.getItems().add(nombre);
    }

    /**
     * Método que se ejecuta al pulsar el botón "Eliminar subasta". Elimina la subasta seleccionada de la lista y del agente
     */
    @FXML
    protected void onButtonEliminarSubastaClick() {
        sellerAgent.eliminarSubasta(listSubastas.getSelectionModel().getSelectedItem());
        listSubastas.getItems().remove(listSubastas.getSelectionModel().getSelectedItem());
        buttonEmpezarSubasta.setDisable(true);
        textFieldPrecioInicial.setDisable(true);
    }

    /**
     * Método que se ejecuta al ser llamado por el agente. Actualiza el precio actual de la subasta seleccionada en la interfaz gráfica.
     */
    protected void actualizarPrecio(String precio) {
        Platform.runLater(() -> labelPrecioActual.setText("Precio actual: " + precio));
    }
    protected void precioFinal(String precio){
        Platform.runLater(() -> labelPrecioActual.setText("Precio final: " + precio));
    }
    protected void añadirComprador(String nombre) {
        //Añadimos un texto con el nombre a vBoxCompradores
        Platform.runLater(() -> listCompradoresGlobal.getItems().add(nombre));
        //vBoxCompradores.getChildren().add(label);

    }

    protected void eliminarComprador (String nombre) {
        //Eliminamos el texto con el nombre de vBoxCompradores
        for (int i = 0; i < listCompradoresGlobal.getItems().size() ; i++) {
           String elemento = listCompradoresGlobal.getItems().get(i);
            if (elemento.equals(nombre)) {
                int finalI = i;
                Platform.runLater(() -> listCompradoresGlobal.getItems().remove(finalI));
                break;
            }
        }
    }

    protected void setSellerAgent(SellerAgent sellerAgent) {
        this.sellerAgent = sellerAgent;
    }

    private void updatePrecio(String message) {
        Platform.runLater(() -> labelPrecioActual.setText(message + "\n"));
    }

    @FXML
    public void initialize(Stage stage) throws IOException {
        this.stage = stage;
        instance = this;

        // Listener para cambios en las listas de subastas
        listSubastas.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                buttonGestionar.setDisable(false);
                buttonEliminarSubasta.setDisable(false);
            } else {
                buttonGestionar.setDisable(true);
                buttonEliminarSubasta.setDisable(true);
            }
        });
    }

    public static SellerController getInstance() {
        return instance;
    }

    public void añadirPuja(String localName, double puja) {
        Label label = new Label(localName + ": " + puja);
        Platform.runLater(() -> vBoxPujas.getChildren().add(label));
    }

    public void setCompradoresSubasta(Subasta subastaSeleccionada) {
        listCompradoresSubasta.getItems().clear();
        for (AID comprador : subastaSeleccionada.getCompradores()) {
            listCompradoresSubasta.getItems().add(comprador.getLocalName());
        }
    }

    public void setPujasSubasta(Subasta subastaSeleccionada) {
        vBoxPujas.getChildren().clear();
        for (ACLMessage puja : subastaSeleccionada.getPujas()) {
            Label label = new Label(puja.getContent());
            Platform.runLater(() -> vBoxPujas.getChildren().add(label));
        }
    }

    public void changeName(String nombre, String s) {
        //Cambiamos el nombre de la subasta en la lista
        if (listSubastas.getItems().contains(nombre)) {
            int index = listSubastas.getItems().indexOf(nombre);
            // Cambiamos el nombre
            Platform.runLater(() -> listSubastas.getItems().set(index, s));
        }
    }
}