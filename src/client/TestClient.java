package client;

import server.Message;

import java.io.*;
import java.net.Socket;

public class TestClient {

    public static void main(String[] args) {
        try (
            Socket socket = new Socket("localhost", 5000);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            // welcome message
            Message welcome = (Message) in.readObject();
            System.out.println("Server: " + welcome);

            // Send Message
            Message myMessage = new Message(Message.TEXT, "client1", "Hello from client!");
            out.writeObject(myMessage);
            out.flush();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
