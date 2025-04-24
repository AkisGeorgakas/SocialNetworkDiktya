package client;

import java.net.*;
import java.io.*;
import java.util.Objects;
import java.util.Scanner;

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

            out.writeObject("Hello there!");
            out.flush();

            Scanner myObj = new Scanner(System.in);

            while(true) {
                System.out.println("Type 1 or 2 to continue:\nLog In: 1\nSign Up: 2");
                String actionCode = myObj.nextLine();
                while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2"))) {
                    System.out.println("Wrong Input");
                    actionCode = myObj.nextLine();
                }
                out.writeObject(actionCode);
                out.flush();

                String userName = "";
                String password = "";
                switch (actionCode) {
                    case "1":
                        System.out.println("Kalispera, pws sas lene parakalw?");
                        userName = myObj.nextLine();

                        System.out.println("Kai poios einai o kwdikos sas?");
                        password = myObj.nextLine();

                        break;
                    case "2":
                        System.out.println("Dhmiourghste ena Username");
                        userName = myObj.nextLine();

                        System.out.println("Dhmiourghste kai enan kwdiko");
                        password = myObj.nextLine();
                        break;
                }
                out.writeObject(userName);
                out.flush();
                out.writeObject(password);
                out.flush();
                String response = (String) in.readObject();
                if(response.equals("Success")){
                    System.out.println("Mpravo sou");
                    break;
                }
            }
            
            
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
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