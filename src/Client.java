import java.net.*;
import java.io.*;

public class Client {
    private Socket connection;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String serverIP;
    private int serverPort;

    public Client(String ip, int port) {
        this.serverIP = ip;
        this.serverPort = port;
    }

    public void startConnection() {
        try {
            connection = new Socket(serverIP, serverPort);

            out = new ObjectOutputStream(connection.getOutputStream());
            in = new ObjectInputStream(connection.getInputStream());

            out.writeObject("Hello there");
            out.flush();

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

    }

    public void stop() {
        try {
            in.close();
            out.close();
            connection.close();

            System.out.println("Connection closed");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client("localhost", 303);
        System.out.println("Established connection with server!");
        client.startConnection();
    }
}