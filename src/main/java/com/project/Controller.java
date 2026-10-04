package com.project;

// Importacions dels components gràfics, de xarxa i esdeveniments de JavaFX i WebSockets
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class Controller {

    // COMPONENTS GRÀFICS INJECTATS DIRECTAMENT DES DELS FITXERS FXML DE SCENE BUILDER
    @FXML private TextField txtServer; // Camp de text de la Vista 1 per a la URL del servidor
    @FXML private TextField txtNombre; // Camp de text de la Vista 1 per al nom del jugador
    @FXML private Label lblJugadorActual; // Etiqueta de la Vista 2 que mostra el nom i punts de l'usuari actiu
    @FXML private GridPane gridSudoku; // Contenidor en quadrícula de la Vista 2 on es munta el tauler
    @FXML private ListView<String> listaPuntuacionesJuego; // Llista lateral de puntuacions en temps real (Vista 2)
    @FXML private ListView<String> listaPuntuacionesResultados; // Llista central de la classificació (Vista 3)

    // MEMÒRIA ESTÀTICA DEL CONTROLADOR (PERSISTÈNCIA ENTRE ESCENES)
    private static WebSocketClient client; // Client WebSocket únic per a la comunicació asíncrona
    private static String nombreJugador; // Nom del jugador triat a la Vista 1
    private static final TextField[][] celdas = new TextField[9][9]; // Matriu 9x9 de cel·les del tauler
    
    // Memòria cau de dades de xarxa per evitar la pèrdua d'informació en canviar de pantalla
    private static String datosTableroGuardados; // Guarda l'últim estat de la cadena del tauler
    private static String datosListaGuardados; // Guarda l'última llista de jugadors rebuda

    // ESDEVENIMENT DE LA VISTA 1: Acció en polsar el botó "Empezar"
    @FXML
    private void onConectarClick() {
        // Extraiem el nom netejant espais en blanc als costats
        nombreJugador = txtNombre.getText().trim();
        // Validem que els camps no estiguin buits abans d'iniciar la connexió per hilos
        if (!nombreJugador.isEmpty() && txtServer.getText() != null) {
            conectarAlServidor(txtServer.getText(), nombreJugador);
        }
    }

    // ESDEVENIMENT DE LA VISTA 2: Acció per anar a la pantalla de rànquing final
    @FXML
    private void onVerClasificacionClick() {
        Main.cambiarEscena("resultats.fxml"); // Cridem l'encaminador de Main
    }

    // ESDEVENIMENT DE LA VISTA 3: Acció per tornar al tauler de joc actiu
    @FXML
    private void onVolverAlJuegoClick() {
        Main.cambiarEscena("joc.fxml"); // Carrega de nou el fitxer del joc de Scene Builder
    }

    // INICIALITZADOR DEL CICLE DE VIDA DE JAVAFX
    // S'executa AUTOMÀTICAMENT cada vegada que un fitxer FXML es renderitza a la pantalla.
    // En aquest punt els components @FXML ja han estat enllaçats i NO són nulls.
    @FXML
    public void initialize() {
        // Si estem carregant la interfície del tauler de joc (joc.fxml)
        if (gridSudoku != null) {
            // Forcem que el text superior es mostri amb 0 pts des del primer segon
            if (lblJugadorActual != null) {
                lblJugadorActual.setText("Jugador: " + nombreJugador + " (0 pts)");
            }
            
            // GESTIÓ CONCURRENT D'INSTÀNCIES DE MATRIU
            // Si la matriu és verge (celdas[0][0] és null), la construïm de zero
            if (celdas[0][0] == null) {
                inicializarComponentesJuego(); 
            } else {
                // Si ja existia, la desenganxem del GridPane vell i la tornem a col·locar al nou layout.
                // Això soluciona el bug de reinici en anar i tornar del rànquing.
                gridSudoku.getChildren().clear();
                gridSudoku.setMinSize(415, 415); // Forcem mida mínima per obrir la línia col·lapsada
                gridSudoku.setPrefSize(415, 415);
                gridSudoku.setAlignment(Pos.CENTER);
                
                // Tornem a incrustar les 81 cel·les guardades en memòria de forma exacta
                for (int f = 0; f < 9; f++) {
                    for (int c = 0; c < 9; c++) {
                        gridSudoku.add(celdas[f][c], c, f);
                    }
                }
            }
            
            // Si teníem informació prèvia a la memòria cau de xarxa, re-pintem l'estat visual actual
            if (datosTableroGuardados != null) rellenarTablero(datosTableroGuardados);
            if (datosListaGuardados != null) actualizarListas(datosListaGuardados);
        }
        
        // Si estem carregant la pantalla de classificació (resultats.fxml)
        if (listaPuntuacionesResultados != null && datosListaGuardados != null) {
            actualizarListas(datosListaGuardados); // Pintem la llista de punts actualitzada
        }
    }

    // CONECTIVITAT CLIENT WEBSOCKET ASÍNCRONA
    private void conectarAlServidor(String url, String nombre) {
        try {
            // Instanciem la classe anònima del client de xarxa en segon pla
            client = new WebSocketClient(new URI(url)) {
                
                // S'executa quan el fil obre correctament el canal de comunicació amb el SudokuServer
                @Override
                public void onOpen(ServerHandshake hand) {
                    send("JOIN:" + nombre); // Enviem la credencial d'entrada del jugador
                    // Saltem a la interfície del joc de forma segura dins el fil gràfic de JavaFX
                    Platform.runLater(() -> Main.cambiarEscena("joc.fxml"));
                }

                // RECEPTOR DE MISSATGES DE XARXA EN TEMPS REAL
                @Override
                public void onMessage(String msg) {
                    // Deleguem el processat del text rebut al fil principal d'interfície (FX Thread)
                    Platform.runLater(() -> {
                        String[] partes = msg.split(":");
                        String comando = partes[0]; // Ordre de xarxa

                        if (comando.equals("INIT")) {
                            datosTableroGuardados = partes[1]; // Guardem la cadena de l'estructura inicial
                            rellenarTablero(datosTableroGuardados);
                        } else if (comando.equals("PLAYERS")) {
                            datosListaGuardados = partes[1]; // Guardem la memòria cau de punts
                            actualizarListas(datosListaGuardados);
                        } else if (comando.equals("CORRECT")) {
                            // Un altre fil concurrent ha encertat un número del tauler
                            int f = Integer.parseInt(partes[1]);
                            int c = Integer.parseInt(partes[2]);
                            String valor = partes[3];
                            
                            if (celdas[f][c] != null) {
                                celdas[f][c].setText(valor); // S'escriu el valor de xarxa
                                // Es pinta de color verd suau i es bloqueja de forma permanent (Requisit)
                                celdas[f][c].setStyle("-fx-background-color: lightgreen; -fx-alignment: center; -fx-font-weight: bold; -fx-text-fill: black;");
                                celdas[f][c].setEditable(false);
                            }
                        }
                    });
                }

                @Override public void onClose(int code, String r, boolean rem) {}
                @Override public void onError(Exception ex) {}
            };
            client.connect(); // Activa el fil de xarxa no bloquejant
        } catch (Exception ex) {
            System.err.println("Error de red: " + ex.getMessage());
        }
    }

    // CONSTRUCTOR DINÀMIC DE LES 81 CEL·LES DE TEXTFIELD
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
                
                // ESCULTADOR DE TEXT CONCURRENT (DETECTA ENTRADES DE L'USUARI)
                tf.textProperty().addListener((obs, viejo, nuevo) -> {
                    // 🟢 FILTRE CRÍTIC DE FLUX: El listener només actuarà si la cel·la és editable per l'usuari.
                    // Si es canvia des del codi de xarxa per actualitzar les pistes, el mètode ho ignorarà, 
                    // solucionant el bug dels 62 punts de cop a l'inici.
                    if (!nuevo.isEmpty() && client != null && tf.isEditable()) {
                        if (!nuevo.matches("[1-9]")) {
                            Platform.runLater(tf::clear); // Si no és un dígit de l'1 al 9, es purga
                        } else {
                            // Enviem la proposta de coordenada al Servidor de WebSockets
                            client.send("UPDATE:" + nombreJugador + ":" + finalF + ":" + finalC + ":" + nuevo);
                            
                            // SUBPROCÉS DE FEEDBACK DE ERROR (ROIG TEMPORAL)
                            Platform.runLater(() -> {
                                try { Thread.sleep(50); } catch (Exception ignored) {}
                                // Si passats 50ms el TextField continua sent editable, vol dir que era incorrecte
                                if (tf.isEditable()) {
                                    int topBox = (finalF % 3 == 0 && finalF > 0) ? 3 : 1;
                                    int leftBox = (finalC % 3 == 0 && finalC > 0) ? 3 : 1;
                                    // Es pinta de color roig suau sense trencar els marges de la regió 3x3
                                    tf.setStyle("-fx-background-color: #ffcdd2; -fx-text-fill: #b71c1c; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-alignment: center; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px;");
                                }
                            });
                        }
                    }
                });
                // Dibuixat de la quadrícula inicial del tauler amb línies separadores grisos
                int topBox = (f % 3 == 0 && f > 0) ? 3 : 1;
                int leftBox = (c % 3 == 0 && c > 0) ? 3 : 1;
                tf.setStyle("-fx-background-color: white; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold;");
                gridSudoku.add(tf, c, f);
            }
        }
    }
    // RECEPTOR VISUAL DE LES PISTES DEL SERVIDOR
    private void rellenarTablero(String datos) {
        if (celdas == null) return;
        String[] valores = datos.split(",");
        int idx = 0;
        for (int f = 0; f < 9; f++) {
            for (int c = 0; c < 9; c++) {
                int val = Integer.parseInt(valores[idx++]);
                if (val != 0 && celdas[f][c] != null) {
                    // 🟢 PROTECCIÓ ESTRUCTURAL CONTRA BUGS DE CAU
                    // Bloquejem la cel·la editable a FALSE ABANS d'escriure el número.
                    // D'aquesta manera el listener detecta que no ha estat l'acció de l'usuari i manté els punts nets.
                    celdas[f][c].setEditable(false);
                    celdas[f][c].setText(String.valueOf(val));
                    // Pintem de color gris fosc pla de pista inicial tal com demana l'enunciat
                    int topBox = (f % 3 == 0 && f > 0) ? 3 : 1;
                    int leftBox = (c % 3 == 0 && c > 0) ? 3 : 1;
                    celdas[f][c].setStyle("-fx-background-color: #eceff1; -fx-text-fill: #37474f; -fx-font-family: 'Segoe UI'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-alignment: center; -fx-border-color: #78909c; -fx-border-width: " + topBox + "px 1px 1px " + leftBox + "px;");
                }
            }
        }
    }
    // GESTOR DE MARCADORS EN SEGON PLA (LLISTES GRÀFIQUES DEL SCENE BUILDER)
    private void actualizarListas(String datos) {
        if (listaPuntuacionesJuego != null) listaPuntuacionesJuego.getItems().clear(); // Neteja Vista 2
        if (listaPuntuacionesResultados != null) listaPuntuacionesResultados.getItems().clear(); // Neteja Vista 3
        String[] entries = datos.split(",");
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                String[] p = entry.split("-");
                if (p.length < 2) continue; // Salva la lectura si hi ha corrupció temporal de canal
                String nombreEnLista = p[0].trim();
                String puntosEnLista = p[1].trim();
                // 🟢 REGLA DE COMPROVACIÓ INDIVIDUAL (IGNORA ESPAIS EN BLANC DE LA RED)
                // Compara el text de forma estricta. Només actualitzarà el marcador superior de la teva
                // finestra quan el registre de missatge rebuda de xarxa coincideixi exactament amb tu.
                if (nombreEnLista.equalsIgnoreCase(nombreJugador.trim()) && lblJugadorActual != null) {
                    lblJugadorActual.setText("Jugador: " + nombreJugador + " (" + puntosEnLista + " pts)");
                }
                // Substituïm la icona de la fletxa trencada per caràcters clàssics '->' compatibles amb Windows
                String formato = nombreEnLista + " -> " + puntosEnLista + " pts";
                // Inyectem el text actualitzat de forma paral·lela a les dues pantalles gràfiques
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