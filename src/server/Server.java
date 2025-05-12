package server;

import java.net.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;

public class Server {


    // // Μοναδικό lock ανά αρχείο
    // public static ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();
    // public static ConcurrentHashMap<String, Queue<String>> fileAccessQueues = new ConcurrentHashMap<>();
    // public static ConcurrentHashMap<String, ScheduledFuture<?>> fileTimers = new ConcurrentHashMap<>();
    // public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);











    // Χάρτης clientID → SocketAddress (IP + port)
    public static ConcurrentHashMap<String, SocketAddress> clientDirectory = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    private final int port;

    public Server(int port) {
        this.port = port;
    }

    private ExecutorService threadPool = Executors.newFixedThreadPool(8); // max 8 clients

    public void start () {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Awaiting for connections...");
            
            while (true) {
                clientSocket = serverSocket.accept();
                System.out.println("Connection established!");
    
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                threadPool.execute(clientHandler); // όχι .start()
                // clientHandler.start();
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
            if(clientSocket != null)clientSocket.close();
            if(serverSocket != null)serverSocket.close();
            if(threadPool != null)threadPool.shutdown();
        } catch (IOException e) {
            System.out.println("Error closing server: " + e.getMessage());
        }

    }

    public static void main(String[] args) {
        Server server = new Server(303);
        server.start();
    }
}