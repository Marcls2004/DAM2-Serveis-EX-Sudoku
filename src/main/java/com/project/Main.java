package com.project;

// Importacions necessàries per a la concurrència i càlculs
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    // Variables globals per emmagatzemar els resultats dels càlculs en paral·lel
    private static double resultatSuma = 0;
    private static double resultatMitjana = 0;
    private static double resultatDesviacio = 0;

    public static void main(String[] args) {
        // Conjunt gran de dades simuladat per fer els càlculs
        double[] dades = {10.0, 12.0, 23.0, 23.0, 16.0, 23.0, 21.0, 16.0};

        // CREACIÓ DE LA CYCLICBARRIER
        // Inicialitzem la barrera per a 3 hilos (les 3 tasques en paral·lel).
        // El segon paràmetre és un Runnable (acció de barrera) que s'executarà 
        // AUTOMÀTICAMENT quan l'últim dels 3 fils arribi a la barrera.
        CyclicBarrier barrera = new CyclicBarrier(3, () -> {
            // Aquest bloc només s'executa quan les 3 tasques han fet el seu .await()
            System.out.println("\n=========================================");
            System.out.println("   RESULTATS FINALS (Sincronitzats)      ");
            System.out.println("=========================================");
            System.out.printf("1. Suma Total:          %.2f\n", resultatSuma);
            System.out.printf("2. Mitjana Aritmètica:  %.2f\n", resultatMitjana);
            System.out.printf("3. Desviació Estàndard: %.2f\n", resultatDesviacio);
            System.out.println("=========================================");
        });

        // TASCA 1 (Runnable): Càlcul de la Suma
        Runnable tascaSuma = () -> {
            System.out.println("[" + Thread.currentThread().getName() + "] Iniciant càlcul de la Suma...");
            double suma = 0;
            for (double d : dades) {
                suma += d;
            }
            resultatSuma = suma; // Guardem el resultat
            System.out.println("[" + Thread.currentThread().getName() + "] Suma calculada. Esperant a la barrera...");
            
            try {
                barrera.await(); // El fil es queda aquí fins que els altres 2 arribin
            } catch (Exception e) {
                System.err.println("Error a la barrera: " + e.getMessage());
            }
        };

        // TASCA 2 (Runnable): Càlcul de la Mitjana
        Runnable tascaMitjana = () -> {
            System.out.println("[" + Thread.currentThread().getName() + "] Iniciant càlcul de la Mitjana...");
            double suma = 0;
            for (double d : dades) {
                suma += d;
            }
            resultatMitjana = suma / dades.length; // Guardem el resultat
            System.out.println("[" + Thread.currentThread().getName() + "] Mitjana calculada. Esperant a la barrera...");
            
            try {
                barrera.await(); // El fil es queda aquí fins que els altres 2 arribin
            } catch (Exception e) {
                System.err.println("Error a la barrera: " + e.getMessage());
            }
        };

        // TASCA 3 (Runnable): Càlcul de la Desviació Estàndard
        Runnable tascaDesviacio = () -> {
            System.out.println("[" + Thread.currentThread().getName() + "] Càlcul de la Desviació Estàndard...");
            
            // Primer necessitem la mitjana per a la fórmula de la desviació
            double suma = 0;
            for (double d : dades) {
                suma += d;
            }
            double mitjana = suma / dades.length;
            
            // Càlcul de la variància
            double sumaDiferenciesQuadrat = 0;
            for (double d : dades) {
                sumaDiferenciesQuadrat += Math.pow(d - mitjana, 2);
            }
            double variancia = sumaDiferenciesQuadrat / dades.length;
            
            // La desviació estàndard és la arrel quadrada de la variància
            resultatDesviacio = Math.sqrt(variancia);
            System.out.println("[" + Thread.currentThread().getName() + "] Desviació calculada. Esperant a la barrera...");
            
            try {
                barrera.await(); // El fil es queda aquí fins que els altres 2 arribin
            } catch (Exception e) {
                System.err.println("Error a la barrera: " + e.getMessage());
            }
        };

        // MOTOR D'EXECUCIÓ (POOL DE FILS)
        // Creem un pool de fils fix per executar les 3 tasques en paral·lel
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Enviem les tasques a l'executor per a la seva execució simultània
        executor.execute(tascaSuma);
        executor.execute(tascaMitjana);
        executor.execute(tascaDesviacio);

        // TANCAMENT CONTROLAT DE L'EXECUTOR
        // Tanquem l'executor perquè no accepti més tasques i alliberi la memòria
        executor.shutdown();
        try {
            // Esperem un màxim de 5 segons a que tots els fils hagin creuat la barrera i acabat
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
