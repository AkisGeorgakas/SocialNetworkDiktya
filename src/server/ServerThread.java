package server;

import java.io.*;
import java.net.Socket;

public class ServerThread extends Thread {
    private Socket clientSocket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private int clientID;

    public ServerThread(Socket socket, int clientID) {
        this.clientSocket = socket;
        this.clientID = clientID;
    }

    public void run() {
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            System.out.println("Client" + clientID + " connected");

            // Welcome message
            Message welcome = new Message(Message.TEXT, "Server", "Welcome client " + clientID);
            out.writeObject(welcome);

            // manage messages
            while (true) {
                Message msg = (Message) in.readObject();
                System.out.println("Received from client" + clientID + ": " + msg);
            }

        } catch (Exception e) {
            System.out.println("Client" + clientID + " disconnected");
        }
    }
}
