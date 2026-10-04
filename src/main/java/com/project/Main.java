package com.project;

// Importacions de l'ecosistema JavaFX per a finestres, escenes i el carregador FXML
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    // Finestra de l'aplicació desada en memòria estàtica per poder canviar de pantalla de forma global
    private static Stage stageActual;
    
    // Instància local del servidor WebSockets emmagatzemada per controlar el seu cicle de vida de tancament
    private static SudokuServer server;

    // PUNT D'ENTRADA GRÀFIC (Cicle de vida de JavaFX)
    // S'executa automàticament immediatament després de cridar al mètode launch(args)
    @Override
    public void start(Stage primaryStage) {
        stageActual = primaryStage;
        
        // MOTOR DE XARXA: Encenem el servidor de WebSockets en un fil independent de fons
        try {
            server = new SudokuServer();
            server.start(); // El servidor comença a escoltar peticions asíncronament al port 8887
        } catch (Exception ignored) {}

        // 🟢 INTERCEPTOR DE CLOENDA (CONTROL DE FIL DE TERMINAL)
        // Solució al bloqueig: capturem l'esdeveniment de polsar la 'X' de la finestra per forçar l'apagada.
        // Si no ho fessim, el fil del servidor es quedaria actiu a la CPU i la terminal de PowerShell mai s'alliberaria.
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("\n[SISTEMA] Cerrando la ventana. Deteniendo hilos de red...");
            try {
                if (server != null) {
                    server.stop(); // Apaga el servidor WebSocket de forma neta de fons
                }
            } catch (Exception ignored) {}
            
            Platform.exit(); // Atura el motor intern gràfic de JavaFX
            System.exit(0);  // Destrueix completament el procés a la memòria i allibera la consola a l'instant
        });

        // CONTROL DE PANTALLA INICIAL: Carreguem el disseny de la Vista 1 (Login)
        cambiarEscena("conexion.fxml");
        stageActual.setTitle("Sudoku Multijugador FXML - Scene Builder"); // Títol superior de la finestra
        stageActual.show(); // Renderitza i mostra la finestra gràfica en pantalla
    }

    // ENCAMINADOR ESTÀTIC DE LES VISTES DE SCENE BUILDER
    // Permet intercanviar el disseny de la finestra des de qualsevol mètode del controlador
    public static void cambiarEscena(String archivoFxml) {
        try {
            // FXMLLoader llegeix el fitxer estructurat d'etiquetes creat per l'editor gràfic Scene Builder
            Parent root = FXMLLoader.load(Main.class.getResource("/com/project/" + archivoFxml));
            
            // Creem una escena nova inyectant-li el layout extret de la carpeta de resources
            Scene nuevaEscena = new Scene(root);
            
            // Substituïm el contingut visual de la finestra actual per la nova vista sense obrir finestres noves
            stageActual.setScene(nuevaEscena);
            
        } catch (Exception e) {
            // Captura de seguretat en cas que un fitxer FXML tingui un error de sintaxi o camí incorrecte
            System.err.println("Error crítico al renderizar la vista FXML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // MÈTODE PRINCIPAL (ENTRY POINT DE LA MÀQUINA VIRTUAL DE JAVA)
    public static void main(String[] args) {
        // Cridada a les utilitats de JavaFX que arranquen els fils interns gràfics i salten al mètode start()
        launch(args);
    }
}
