package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMain {
    private static final int PORT = 5000;
    private static AtomicInteger clientCounter = new AtomicInteger(1);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientID = clientCounter.getAndIncrement();

                ServerThread thread = new ServerThread(clientSocket, clientID);
                thread.start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
