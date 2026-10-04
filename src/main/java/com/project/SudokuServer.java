package com.server;

import java.io.*;
import java.net.*;

public class Main {
    public static void main(String[] args) {
        try {
            // Crear socket del servidor en el port 12345
            ServerSocket servidorSocket = new ServerSocket(12345);
            System.out.println("\n---------------------------------------------");
            System.out.println("Servidor esperant connexions...");

            // Acceptar connexió del client
            Socket socket = servidorSocket.accept();
            System.out.println("Client connectat!");

            // Crear fluxos per llegir i escriure dades
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);

            // Llegir missatge del client
            String missatgeClient = input.readLine();
            System.out.println("Missatge del client: " + missatgeClient);

            // Respondre al client
            output.println("Hola des del servidor!");

            // Tancar connexions
            input.close();
            output.close();
            socket.close();
            servidorSocket.close();
            System.out.println("---------------------------------------------\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

