package server;

import java.net.*;
import java.io.*;

public class Server {
    private ServerSocket serverSocket;
    private Socket clientSocket;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    private int port;

    public Server(int port) {
        this.port = port;
    }

    public void start () {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Awaiting for connections...");
            
            while (true) {
                clientSocket = serverSocket.accept();
                System.out.println("Connection established!");
    
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandler.start();
            }

        }
        catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void stop() {
        try {
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }

    public static void main(String[] args) {
        Server server = new Server(303);
        server.start();
    }
}