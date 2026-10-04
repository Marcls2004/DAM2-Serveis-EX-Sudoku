package com.project;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    private static Stage stageActual;
    private static SudokuServer server; // Guardamos la referencia del servidor

    @Override
    public void start(Stage primaryStage) {
        stageActual = primaryStage;
        
        // Encendemos el servidor de WebSockets en segundo plano
        try {
            server = new SudokuServer();
            server.start();
        } catch (Exception ignored) {}

        // 🟢 SOLUCIÓN AL CIERRE EN TERMINAL: 
        // Cuando el usuario pulse la 'X' de la ventana, se apaga todo automáticamente
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("\n[SISTEMA] Cerrando la ventana. Deteniendo hilos de red...");
            try {
                if (server != null) {
                    server.stop(); // Apaga el servidor WebSocket de forma limpia
                }
            } catch (Exception ignored) {}
            Platform.exit();
            System.exit(0); // Mata el proceso y libera la consola de PowerShell de golpe
        });

        // Cargamos la interfaz de Login inicial usando la ruta de resources
        cambiarEscena("conexion.fxml");
        stageActual.setTitle("Sudoku Multijugador FXML - Scene Builder");
        stageActual.show();
    }

    // Cambia dinámicamente el layout gráfico de la ventana usando FXML
    public static void cambiarEscena(String archivoFxml) {
        try {
            Parent root = FXMLLoader.load(Main.class.getResource("/com/project/" + archivoFxml));
            Scene nuevaEscena = new Scene(root);
            stageActual.setScene(nuevaEscena);
        } catch (Exception e) {
            System.err.println("Error crítico al renderizar la vista FXML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
