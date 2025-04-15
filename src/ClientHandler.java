import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public void run() {        
        try {
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());

            String clientMessage = (String) inStream.readObject();

            System.out.println("Client message: " + clientMessage);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}