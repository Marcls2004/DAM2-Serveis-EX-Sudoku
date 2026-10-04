package com.project;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class Controller {

    // Componentes inyectados directamente desde los FXML de Scene Builder
    @FXML private TextField txtServer;
    @FXML private TextField txtNombre;
    @FXML private Label lblJugadorActual;
    @FXML private GridPane gridSudoku;
    @FXML private ListView<String> listaPuntuacionesJuego;
    @FXML private ListView<String> listaPuntuacionesResultados;

    private static WebSocketClient client;
    private static String nombreJugador;
    private static final TextField[][] celdas = new TextField[9][9];
    
    // Variables estáticas para recordar los datos de red al cambiar entre ventanas FXML
    private static String datosTableroGuardados;
    private static String datosListaGuardados;

    // Evento de la Vista 1 (Login)
    @FXML
    private void onConectarClick() {
        nombreJugador = txtNombre.getText().trim();
        if (!nombreJugador.isEmpty() && txtServer.getText() != null) {
            conectarAlServidor(txtServer.getText(), nombreJugador);
        }
    }

    // Evento de la Vista 2 (Ver Resultados)
    @FXML
    private void onVerClasificacionClick() {
        Main.cambiarEscena("resultats.fxml");
    }

    // Evento de la Vista 3 (Volver a Jugar)
    @FXML
    private void onVolverAlJuegoClick() {
        Main.cambiarEscena("joc.fxml");
    }

    // 🟢 EL TRUCO REQUERIDO: Este método se ejecuta AUTOMÁTICAMENTE cada vez que JavaFX 
    // termina de renderizar un FXML en la pantalla. Aquí los componentes ya NO son null.
    // Este mètode s'executa automàticament cada vegada que es carrega una vista FXML
    @FXML
    public void initialize() {
        // Si estamos cargando la vista del juego (joc.fxml)
        if (gridSudoku != null) {
            // 🟢 SOLUCIÓN: Añadimos (0 pts) aquí para que aparezca desde el primer segundo antes de recibir datos de red
            if (lblJugadorActual != null) {
                lblJugadorActual.setText("Jugador: " + nombreJugador + " (0 pts)");
            }
            
            // Si la matriz gráfica de celdas aún no se ha creado en memoria, la inicializamos
            if (celdas[0][0] == null) {
                inicializarComponentesJuego(); 
            } else {
                // Si ya existía, simplemente la volvemos a enganchar al GridPane actual de Scene Builder
                gridSudoku.getChildren().clear();
                gridSudoku.setMinSize(415, 415);
                gridSudoku.setPrefSize(415, 415);
                gridSudoku.setAlignment(Pos.CENTER);
                
                for (int f = 0; f < 9; f++) {
                    for (int c = 0; c < 9; c++) {
                        gridSudoku.add(celdas[f][c], c, f);
                    }
                }
            }
            
            // Refresquemos las dades de xarxa per assegurar que tot es manté al seu lloc
            if (datosTableroGuardados != null) rellenarTablero(datosTableroGuardados);
            if (datosListaGuardados != null) actualizarListas(datosListaGuardados);
        }
        
        // Si estamos cargando la clasificación final (resultats.fxml)
        if (listaPuntuacionesResultados != null && datosListaGuardados != null) {
            actualizarListas(datosListaGuardados);
        }
    }



    private void conectarAlServidor(String url, String nombre) {
        try {
            client = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake hand) {
                    send("JOIN:" + nombre);
                    Platform.runLater(() -> Main.cambiarEscena("joc.fxml"));
                }

                @Override
                public void onMessage(String msg) {
                    Platform.runLater(() -> {
                        String[] partes = msg.split(":");
                        String comando = partes[0];

                        if (comando.equals("INIT")) {
                            datosTableroGuardados = partes[1];
                            rellenarTablero(datosTableroGuardados);
                        } else if (comando.equals("PLAYERS")) {
                            datosListaGuardados = partes[1];
                            actualizarListas(datosListaGuardados);
                        } else if (comando.equals("CORRECT")) {
                            int f = Integer.parseInt(partes[1]);
                            int c = Integer.parseInt(partes[2]);
                            String valor = partes[3];
                            
                            if (celdas[f][c] != null) {
                                celdas[f][c].setText(valor);
                                // 🟢 SOLO CUANDO ACIERTAS: Se pone de fondo color verde y se bloquea
                                celdas[f][c].setStyle("-fx-background-color: lightgreen; -fx-alignment: center; -fx-font-weight: bold; -fx-text-fill: black;");
                                celdas[f][c].setEditable(false);
                            }
                        }
                    });
                }

                @Override public void onClose(int code, String r, boolean rem) {}
                @Override public void onError(Exception ex) {}
            };
            client.connect();
        } catch (Exception ex) {
            System.err.println("Error de red: " + ex.getMessage());
        }
    }

     private void inicializarComponentesJuego() {
        if (gridSudoku == null) return;
        
        gridSudoku.getChildren().clear();
        gridSudoku.setMinSize(415, 415);
        gridSudoku.setPrefSize(415, 415);
        gridSudoku.setAlignment(Pos.CENTER);

        for (int f = 0; f < 9; f++) {
            for (int c = 0; c < 9; c++) {
                TextField tf = new TextField();
                tf.setPrefSize(45, 45);
                tf.setAlignment(Pos.CENTER);
                celdas[f][c] = tf;

                int finalF = f;
                int finalC = c;
                
                // Escuchamos los cambios de texto en las casillas
                tf.textProperty().addListener((obs, viejo, nuevo) -> {
                    // 🟢 CORRECCIÓN CRÍTICA DE CONCURRENCIA: 
                    // El listener SOLO enviará datos a la red si el TextField está activo y es EDITABLE por el usuario.
                    // Si se modifica mediante código de fondo (como las pistas de red), se ignorará.
                    if (!nuevo.isEmpty() && client != null && tf.isEditable()) {
                        if (!nuevo.matches("[1-9]")) {
                            Platform.runLater(tf::clear);
                        } else {
                            client.send("UPDATE:" + nombreJugador + ":" + finalF + ":" + finalC + ":" + nuevo);
                            
                            Platform.runLater(() -> {
                                try { Thread.sleep(50); } catch (Exception ignored) {}
                                if (tf.isEditable()) { 
                                    int topBox = (finalF % 3 == 0 && finalF > 0) ? 3 : 1;
                                    int leftBox = (finalC % 3 == 0 && finalC > 0) ? 3 : 1;
                                    tf.setStyle("-fx-background-color: #ffcdd2; -fx-text-fill: #b71c1c; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-alignment: center; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px;");
                                }
                            });
                        }
                    }
                });

                int topBox = (f % 3 == 0 && f > 0) ? 3 : 1;
                int leftBox = (c % 3 == 0 && c > 0) ? 3 : 1;
                tf.setStyle("-fx-background-color: white; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold;");
                gridSudoku.add(tf, c, f);
            }
        }
    }

    private void rellenarTablero(String datos) {
        if (celdas == null) return;
        String[] valores = datos.split(",");
        int idx = 0;
        for (int f = 0; f < 9; f++) {
            for (int c = 0; c < 9; c++) {
                int val = Integer.parseInt(valores[idx++]);
                if (val != 0 && celdas[f][c] != null) {
                    // 🟢 CORRECCIÓN CRÍTICA DE ORDEN:
                    // Bloqueamos la celda ANTES de escribir el texto. Así, cuando se dispare 
                    // el listener, detectará que isEditable() es FALSE y no enviará nada al servidor.
                    celdas[f][c].setEditable(false);
                    celdas[f][c].setText(String.valueOf(val));
                    
                    int topBox = (f % 3 == 0 && f > 0) ? 3 : 1;
                    int leftBox = (c % 3 == 0 && c > 0) ? 3 : 1;
                    celdas[f][c].setStyle("-fx-background-color: #eceff1; -fx-text-fill: #37474f; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-alignment: center; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px;");
                }
            }
        }
    }



    private void actualizarListas(String datos) {
        if (listaPuntuacionesJuego != null) listaPuntuacionesJuego.getItems().clear();
        if (listaPuntuacionesResultados != null) listaPuntuacionesResultados.getItems().clear();

        String[] entries = datos.split(",");
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                String[] p = entry.split("-");
                if (p.length < 2) continue; // Evita errores si la cadena viene corrupta de la red
                
                String nombreEnLista = p[0].trim();
                String puntosEnLista = p[1].trim();
                
                // 🟢 AISLAMIENTO COMPLETO: Comparamos ignorando mayúsculas/minúsculas y quitando espacios.
                // Esto garantiza que si el acierto es de 'Albert', no se inyecte en la pantalla de 'Marc'.
                if (nombreEnLista.equalsIgnoreCase(nombreJugador.trim()) && lblJugadorActual != null) {
                    lblJugadorActual.setText("Jugador: " + nombreJugador + " (" + puntosEnLista + " pts)");
                }

                // Cambiamos la flecha rota por dos guiones '->' para que la consola de Windows lo pinte limpio
                String formato = nombreEnLista + " -> " + puntosEnLista + " pts";
                
                if (listaPuntuacionesJuego != null) {
                    listaPuntuacionesJuego.getItems().add(formato);
                }
                if (listaPuntuacionesResultados != null) {
                    listaPuntuacionesResultados.getItems().add(formato);
                }
            }
        }
    }
}
