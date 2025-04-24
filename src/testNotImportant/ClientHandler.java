package testNotImportant;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final int clientID;

    public ClientHandler(Socket socket, int clientID) {
        this.socket = socket;
        this.clientID = clientID;
    }

    @Override
    public void run() {
        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            String welcomeMsg = "Welcome client " + clientID;
            out.writeUTF(welcomeMsg);
            System.out.println("✅ Sent to client" + clientID + ": " + welcomeMsg);

            
            while (true) {
                String clientMsg = in.readUTF();
                System.out.println("📨 client" + clientID + " says: " + clientMsg);
                
            }

        } catch (IOException e) {
            System.out.println("client.Client " + clientID + " disconnected.");
        }
    }
}
