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
    private boolean flag = false;
    private Scanner myObj = new Scanner(System.in);

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

            while(!flag) {
                System.out.println("\n1) Log In\n2) Sign Up\n3) Exit");
                String actionCode = myObj.nextLine();
                while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3"))) {
                    System.out.println("Wrong Input");
                    actionCode = myObj.nextLine();                    
                }

                out.writeObject(actionCode);
                out.flush();

                switch (actionCode) {
                    case "1":
                        login();
                        break;
                    case "2":
                        signup();
                        break;
                    case "3":
                        stop();
                        break;
                }
            }
            myObj.close();

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
            flag = true;

            System.out.println("Connection closed");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public void login() throws IOException, ClassNotFoundException {
        System.out.println("Kalispera, pws sas lene parakalw?");
        String userName = myObj.nextLine();

        System.out.println("Kai poios einai o kwdikos sas?");
        String password = myObj.nextLine();

        out.writeObject(userName);
        out.flush();
        out.writeObject(password);
        out.flush();

        String response = (String) in.readObject();
        if(response.equals("Success")){
            System.out.println("Mpravo sou");
        }
    }

    public void signup() throws IOException, ClassNotFoundException {
        System.out.println("Dhmiourghste ena Username");
        String userName = myObj.nextLine();

        System.out.println("Dhmiourghste kai enan kwdiko");
        String password = myObj.nextLine();

        out.writeObject(userName);
        out.flush();
        out.writeObject(password);
        out.flush();

        String response = (String) in.readObject();
        if(response.equals("Success")){
            System.out.println("Mpravo sou");
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client("localhost", 303);
        System.out.println("Established connection with server!");
        client.startConnection();
    }
}