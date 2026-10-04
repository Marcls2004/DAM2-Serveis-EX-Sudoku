package com.project;

// Importacions per a la gestió de xarxa a través de WebSockets i estructures de dades
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

    // Port estàndard pel qual escoltarà les connexions entrants dels clients de Joc
    private static final int PORT = 8887;
    
    // MATRIZ BASE DE SOLUCIÓ VALIDA
    // Plantilla inicial perfectament verificada que mai conté elements duplicats a cap línia ni regió.
    // S'utilitza com a matriu origen per a totes les transformacions geomètriques.
    private static final int[][] SOLUCION_BASE = {
        {5,3,4, 6,7,8, 9,1,2}, {6,7,2, 1,9,5, 3,4,8}, {1,9,8, 3,4,2, 5,6,7},
        {8,5,9, 7,6,1, 4,2,3}, {4,2,6, 8,5,3, 7,9,1}, {7,1,3, 9,2,4, 8,5,6},
        {9,6,1, 5,3,7, 2,8,4}, {2,8,7, 4,1,9, 6,3,5}, {3,4,5, 2,8,6, 1,7,9}
    };
    
    // MÀSCARA BINÀRIA DE CONFIGURACIÓ ORIGINAL
    // 1 indica casella plena (pista inicial), 0 indica casella buida editable.
    // Garanteix que la distribució i dificultat dels forats del Sudoku sempre respecti l'exercici demanat.
    private static final int[][] MASCARA_BASE = {
        {0,0,1, 1,0,0, 0,1,1}, {0,0,0, 1,0,0, 0,1,0}, {0,0,0, 0,0,1, 1,0,0},
        {1,1,0, 0,1,1, 0,0,0}, {0,0,1, 1,1,0, 1,1,0}, {1,1,1, 0,0,1, 1,1,0},
        {0,1,0, 0,0,1, 1,0,0}, {0,1,1, 0,0,1, 0,0,1}, {0,0,0, 0,1,0, 0,0,0}
    };

    // Matrius dinàmiques en memòria on es generarà el nivell modificat actual
    private final int[][] solucionAleatoria = new int[9][9];
    private final int[][] mapaInicialAleatorio = new int[9][9];

    // COLECIONS CONCURRENTS SEGURES PER A MULTIJUGADOR (THREAD-SAFE)
    // Connecten els canals interns de xarxa (WebSocket) amb les credencials del jugador.
    private final ConcurrentHashMap<WebSocket, String> jugadores = new ConcurrentHashMap<>();
    // Enmagatzema les puntuacions individuals evitant condicions de carrera.
    private final ConcurrentHashMap<String, Integer> puntuaciones = new ConcurrentHashMap<>();
    
    // Contadors interns utilitzats per al control lògic de caselles i sincronització
    private int casillasInicialesFijas = 0;
    private int aciertosDeJugadores = 0;

    // CONSTRUCTOR DEL SERVIDOR
    // Obre el socket al port indicat i inicia automàticament la creació del nivell de joc
    public SudokuServer() {
        super(new InetSocketAddress(PORT));
        generarSudokuAleatorio();
    }

    // ALGORITME DE BARREJA I METAMORFOSI GEOMÈTRICA DEL SUDOKU
    // Transforma el mapa base de forma que sigui un Sudoku totalment nou però 100% lícit matemàticament.
    private void generarSudokuAleatorio() {
        // 1. Clonem les plantilles constants a una estructura dinàmica de memòria per treballar nets
        int[][] solTmp = new int[9][9];
        int[][] maskTmp = new int[9][9];
        for (int f = 0; f < 9; f++) {
            System.arraycopy(SOLUCION_BASE[f], 0, solTmp[f], 0, 9);
            System.arraycopy(MASCARA_BASE[f], 0, maskTmp[f], 0, 9);
        }

        Random rand = new Random();

        // 2. MUTACIÓ ESTRUCTURAL: Intercanviar files d'una mateixa regió (0-2, 3-5, 6-8)
        // D'aquesta manera s'altera la posició de les pistes grises sense trencar la regla de l'eix vertical
        for (int region = 0; region < 3; region++) {
            int baseFila = region * 3;
            int f1 = baseFila + rand.nextInt(3);
            int f2 = baseFila + rand.nextInt(3);
            if (f1 != f2) {
                int[] tempSol = solTmp[f1]; solTmp[f1] = solTmp[f2]; solTmp[f2] = tempSol;
                int[] tempMask = maskTmp[f1]; maskTmp[f1] = maskTmp[f2]; maskTmp[f2] = tempMask;
            }
        }

        // 3. MUTACIÓ ESTRUCTURAL: Intercanviar columnes d'una mateixa regió (0-2, 3-5, 6-8)
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

        // 4. MUTACIÓ ESTRUCTURAL: Rotació aleatòria del tauler complet (0, 90, 180 o 270 graus)
        // Reposiciona aleatòriament els buits i les pistes sobre els quatre eixos
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

        // 5. BARREJA DE SIMBOLS NUMÈRICS: Permutació aleatòria dels números de l'1 al 9
        // Garanteix que els números que apareixen hagin canviat totalment el seu valor visual (ex: els 4 passen a ser 2)
        List<Integer> digitos = new ArrayList<>();
        for (int i = 1; i <= 9; i++) digitos.add(i);
        Collections.shuffle(digitos);

        // Netejem marcadors previs de memòria abans del llançament
        puntuaciones.clear(); 
        casillasInicialesFijas = 0;
        aciertosDeJugadores = 0; 
        
        // Mapejat final creuat combinant les mutacions visuals amb la solució matemàtica vàlida
        for (int f = 0; f < 9; f++) {
            for (int c = 0; c < 9; c++) {
                int numSol = solTmp[f][c];
                int tienePista = maskTmp[f][c];

                // S'aplica el canvi numèric desordenat a la solució real
                solucionAleatoria[f][c] = digitos.get(numSol - 1);
                
                // Si la màscara indica que és una casella del principi, hereta la pista ja calculada lícitament
                if (tienePista == 1) {
                    mapaInicialAleatorio[f][c] = solucionAleatoria[f][c];
                    casillasInicialesFijas++; // Comptem la pista inicial per al registre
                } else {
                    mapaInicialAleatorio[f][c] = 0; // Queda buit per al client
                }
            }
        }
        System.out.println("[SISTEMA] Caché de puntos limpiada. Sudoku nuevo inicializado a 0 pts.");
    }

    // DISPATCHER CENTRAL DE MISSATGES DE XARXA
    // S'executa asíncronament cada vegada que rep una cadena de text des de qualsevol WebSocketClient
    @Override
    public void onMessage(WebSocket conn, String message) {
        // Separem el missatge de text utilitzant el divisor de l'arquitectura de protocol propia
        String[] partes = message.split(":");
        String comando = partes[0]; // El primer fragment determina el camí del flux

        // COMANDO 1: Connexió inicial de nous hilos de joc al Servidor
        if (comando.equals("JOIN")) {
            String nombre = partes[1];
            jugadores.put(conn, nombre); // Guardem el canal associat al nom
            puntuaciones.putIfAbsent(nombre, 0); // Iniciem marcador net en 0 pts
            
            // Enviem exclusivament a aquest client la cadena de text amb les pistes inicials grises
            conn.send("INIT:" + tableroAString(mapaInicialAleatorio));
            enviarActualizacionGlobal(); // Avisem de la llista general de punts actualitzada
            
        // COMANDO 2: Un jugador intenta resoldre una casella buida des d'un input del Scene Builder
        } else if (comando.equals("UPDATE")) {
            String nombre = partes[1];
            int fila = Integer.parseInt(partes[2]);
            int col = Integer.parseInt(partes[3]);
            int valor = Integer.parseInt(partes[4]);

            // SISTEMA COMPETITIU MULTIJUGADOR DE RECOMPENSES I PENALITZACIONS
            if (solucionAleatoria[fila][col] == valor) {
                // S'incrementen 2 punts ÚNICAMENT al perfil de l'usuari que ha encertat la casella
                puntuaciones.put(nombre, puntuaciones.get(nombre) + 2);
                
                // BROADCAST MASIU: Enviem la directiva 'CORRECT' a TOTS els jugadors connectats concurrentment 
                // per a que la seva interfície gràfica de JavaFX bloquegi la cel·la en verd al mateix temps
                broadcast("CORRECT:" + fila + ":" + col + ":" + valor);
                
                aciertosDeJugadores++; // Registrem l'avançament cap a la meta del tauler
                enviarActualizacionGlobal(); // Refresquem els marcadors en temps real de les caixes de Scene Builder

                // REINICIO ELIMINADO: La lògica de finalització s'ha extret per permetre un flux de final complet 
                // estable i fix sense canvis bruscos de pantalla fins que els jugadors decideixin de forma voluntària
                
            } else {
                // Penalització per errada: Es resta 1 punt d'enmig, assegurant que el total no baixi de 0
                puntuaciones.put(nombre, Math.max(0, puntuaciones.get(nombre) - 1));
                enviarActualizacionGlobal(); // S'emet l'actualització de dades general
            }
        }
    }

    // SISTEMA DE REFRESCH GLOBAL DE CLASSIFICACIÓ ORDENADA NUMÈRICAMENT
    // Envia en bucle i de forma simultània el marcador actual de major a menor puntuació a tots els clients
    private void enviarActualizacionGlobal() {
        // Convertim el ConcurrentHashMap a una estructura de llista per poder utilitzar funcions d'ordenament
        List<Map.Entry<String, Integer>> listaOrdenada = new ArrayList<>(puntuaciones.entrySet());
        // ORDENACIÓ COMPETITIVA (DE MAJOR A MENOR): Compara els valors de punts col·locant al capdavant el valor més alt
        listaOrdenada.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        // Empaqueta els noms i punts en una cadena separada per guions i comes per a la transmissió per hilos
        StringBuilder sb = new StringBuilder("PLAYERS:");
        for (Map.Entry<String, Integer> entry : listaOrdenada) {
            sb.append(entry.getKey()).append("-").append(entry.getValue()).append(",");
        }
        broadcast(sb.toString()); // Emet el paquet textual resultant
    }
    // SERIALITZADOR DE MATRIUS A TEXT
    // Tradueix les matrius numèriques a una única línia separada per comes fàcil de viatjar pel corrent de xarxa
    private String tableroAString(int[][] matriz) {
        StringBuilder sb = new StringBuilder();
        for (int[] fila : matriz) {
            for (int val : fila) sb.append(val).append(",");
        }
        return sb.toString();
    }
    // CAPTURADOR DE FALLS DE COMMUNICACIÓ INTERNA DE LES UTILLITATS WEBSOCKETS
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[SERVIDOR] Error en red: " + ex.getMessage());
    }
    // CONFIRMACIÓ D'ENCÈS DEL FIL DEL SERVIDOR WEBSOCKET
    @Override
    public void onStart() {
        System.out.println("[SERVIDOR] Servicio WebSockets levantado en el puerto " + PORT);
    }
    // DETECTOR D'OBERTURA DE CANAL DE FIL BUID CONECTANT UN NOU JUGADOR
    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {}
    // GESTOR DE DESCONNEXIONS I CONTROL DE RECURSOS CONCURRENT
    // Si un jugador tanca la seva finestra de JavaFX, es purga el seu nom i puntuació de la memòria de l'equip
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String nombre = jugadores.remove(conn); // Elimina el canal de dades d'escriptura
        if (nombre != null) {
            puntuaciones.remove(nombre); // Elimina el marcador per no embrutar la pantalla del ranking
            enviarActualizacionGlobal(); // Notifica el canvi en segon pla a la resta
        }
    }
}