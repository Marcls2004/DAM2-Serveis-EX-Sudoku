package com.project;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class SudokuServer extends WebSocketServer {

    private static final int PORT = 8887;
    
    // 🟢 MATRIZ DEFINITIVA: Plantilla base de Sudoku 100% válida y verificada matemáticamente
    private static final int[][] SOLUCION_BASE = {
        {5,3,4, 6,7,8, 9,1,2}, {6,7,2, 1,9,5, 3,4,8}, {1,9,8, 3,4,2, 5,6,7},
        {8,5,9, 7,6,1, 4,2,3}, {4,2,6, 8,5,3, 7,9,1}, {7,1,3, 9,2,4, 8,5,6},
        {9,6,1, 5,3,7, 2,8,4}, {2,8,7, 4,1,9, 6,3,5}, {3,4,5, 2,8,6, 1,7,9}
    };
    
    // 🟢 MÁSCARA BINARIA: 1 significa que la casilla se muestra (gris), 0 significa vacía.
    private static final int[][] MASCARA_BASE = {
        {0,0,1, 1,0,0, 0,1,1}, {0,0,0, 1,0,0, 0,1,0}, {0,0,0, 0,0,1, 1,0,0},
        {1,1,0, 0,1,1, 0,0,0}, {0,0,1, 1,1,0, 1,1,0}, {1,1,1, 0,0,1, 1,1,0},
        {0,1,0, 0,0,1, 1,0,0}, {0,1,1, 0,0,1, 0,0,1}, {0,0,0, 0,1,0, 0,0,0}
    };

    private final int[][] solucionAleatoria = new int[9][9];
    private final int[][] mapaInicialAleatorio = new int[9][9];

    private final ConcurrentHashMap<WebSocket, String> jugadores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> puntuaciones = new ConcurrentHashMap<>();
    
    // Contadores para asegurar el flujo de inicio limpio a 0 pts y fin de partida lícito
    private int casillasInicialesFijas = 0;
    private int aciertosDeJugadores = 0;

    public SudokuServer() {
        super(new InetSocketAddress(PORT));
        generarSudokuAleatorio();
    }

        private void generarSudokuAleatorio() {
        // 1. Instanciamos y clonamos las matrices base temporales en memoria
        int[][] solTmp = new int[9][9];
        int[][] maskTmp = new int[9][9];
        for (int f = 0; f < 9; f++) {
            System.arraycopy(SOLUCION_BASE[f], 0, solTmp[f], 0, 9);
            System.arraycopy(MASCARA_BASE[f], 0, maskTmp[f], 0, 9);
        }

        Random rand = new Random();

        // 2. MEZCLA Estructural: Intercambiar filas dentro de sus regiones de 3 en 3
        for (int region = 0; region < 3; region++) {
            int baseFila = region * 3;
            int f1 = baseFila + rand.nextInt(3);
            int f2 = baseFila + rand.nextInt(3);
            if (f1 != f2) {
                int[] tempSol = solTmp[f1]; solTmp[f1] = solTmp[f2]; solTmp[f2] = tempSol;
                int[] tempMask = maskTmp[f1]; maskTmp[f1] = maskTmp[f2]; maskTmp[f2] = tempMask;
            }
        }

        // 3. MEZCLA Estructural: Intercambiar columnas dentro de sus regiones de 3 en 3
        for (int region = 0; region < 3; region++) {
            int baseCol = region * 3;
            int c1 = baseCol + rand.nextInt(3);
            int c2 = baseCol + rand.nextInt(3);
            if (c1 != c2) {
                for (int f = 0; f < 9; f++) {
                    int tSol = solTmp[f][c1]; solTmp[f][c1] = solTmp[f][c2]; solTmp[f][c2] = tSol;
                    int tMask = maskTmp[f][c1]; maskTmp[f][c1] = maskTmp[f][c2]; maskTmp[f][c2] = tMask;
                }
            }
        }

        // 4. MEZCLA Estructural: Rotación aleatoria completa del tablero
        int rotaciones = rand.nextInt(4);
        for (int r = 0; r < rotaciones; r++) {
            int[][] solRotada = new int[9][9];
            int[][] maskRotada = new int[9][9];
            for (int f = 0; f < 9; f++) {
                for (int c = 0; c < 9; c++) {
                    solRotada[c][8 - f] = solTmp[f][c];
                    maskRotada[c][8 - f] = maskTmp[f][c];
                }
            }
            solTmp = solRotada;
            maskTmp = maskRotada;
        }

        // 5. Mapeo final de dígitos aleatorios
        List<Integer> digitos = new ArrayList<>();
        for (int i = 1; i <= 9; i++) digitos.add(i);
        Collections.shuffle(digitos);

        // 🟢 SOLUCIÓN AL BUG DE INICIO: Vaciamos por completo el mapa concurrente de puntos antiguos
        puntuaciones.clear(); 
        casillasInicialesFijas = 0;
        aciertosDeJugadores = 0; 
        
        for (int f = 0; f < 9; f++) {
            for (int c = 0; c < 9; c++) {
                int numSol = solTmp[f][c];
                int tienePista = maskTmp[f][c];

                solucionAleatoria[f][c] = digitos.get(numSol - 1);
                
                if (tienePista == 1) {
                    mapaInicialAleatorio[f][c] = solucionAleatoria[f][c];
                    casillasInicialesFijas++;
                } else {
                    mapaInicialAleatorio[f][c] = 0;
                }
            }
        }
        System.out.println("[SISTEMA] Caché de puntos limpiada. Sudoku nuevo inicializado a 0 pts.");
    }


    @Override
    public void onMessage(WebSocket conn, String message) {
        String[] partes = message.split(":");
        String comando = partes[0];

        if (comando.equals("JOIN")) {
            String nombre = partes[1];
            jugadores.put(conn, nombre);
            puntuaciones.putIfAbsent(nombre, 0);
            conn.send("INIT:" + tableroAString(mapaInicialAleatorio));
            enviarActualizacionGlobal();
            
        } else if (comando.equals("UPDATE")) {
            String nombre = partes[1];
            int fila = Integer.parseInt(partes[2]);
            int col = Integer.parseInt(partes[3]);
            int valor = Integer.parseInt(partes[4]);

            if (solucionAleatoria[fila][col] == valor) {
                // 🟢 LÓGICA COMPETITIVA: Suma exclusivamente al jugador que envía el acierto
                puntuaciones.put(nombre, puntuaciones.get(nombre) + 2);
                broadcast("CORRECT:" + fila + ":" + col + ":" + valor);
                
                aciertosDeJugadores++; 
                enviarActualizacionGlobal();

                // 🟢 REINICIO JUSTO: Se activa al rellenar los huecos restantes del tablero
                if ((casillasInicialesFijas + aciertosDeJugadores) == 81) {
                    System.out.println("[SERVIDOR] ¡Tablero completado lícitamente! Reiniciando partida...");
                    puntuaciones.keySet().forEach(k -> puntuaciones.put(k, 0));
                    generarSudokuAleatorio();
                    broadcast("RESTART:" + tableroAString(mapaInicialAleatorio));
                    enviarActualizacionGlobal();
                }
            } else {
                puntuaciones.put(nombre, Math.max(0, puntuaciones.get(nombre) - 1));
                enviarActualizacionGlobal();
            }
        }
    }

    private void enviarActualizacionGlobal() {
        List<Map.Entry<String, Integer>> listaOrdenada = new ArrayList<>(puntuaciones.entrySet());
        listaOrdenada.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        StringBuilder sb = new StringBuilder("PLAYERS:");
        for (Map.Entry<String, Integer> entry : listaOrdenada) {
            sb.append(entry.getKey()).append("-").append(entry.getValue()).append(",");
        }
        broadcast(sb.toString());
    }

    private String tableroAString(int[][] matriz) {
        StringBuilder sb = new StringBuilder();
        for (int[] fila : matriz) {
            for (int val : fila) sb.append(val).append(",");
        }
        return sb.toString();
    }

    @Override 
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[SERVIDOR] Error en red: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[SERVIDOR] Servicio WebSockets levantado en el puerto " + PORT);
    }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {}

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String nombre = jugadores.remove(conn);
        if (nombre != null) {
            puntuaciones.remove(nombre);
            enviarActualizacionGlobal();
        }
    }
}
