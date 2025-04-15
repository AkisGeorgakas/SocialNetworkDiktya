package testNotImportant;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final int PORT = 12345;
    private static final int MAX_CLIENTS = 8;
    private static AtomicInteger clientCounter = new AtomicInteger(1);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                int clientID = clientCounter.getAndIncrement();

                System.out.println("New client connected: client" + clientID);

                
                ClientHandler handler = new ClientHandler(clientSocket, clientID);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }
}
