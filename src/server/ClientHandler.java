package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private UsersLoader usersLoader = new UsersLoader("../data/users.txt");
    private boolean flag = false;
    private String clientId;

    public ClientHandler(Socket socket) throws IOException {
        this.clientSocket = socket;
    }

    public void run() {     
        try {
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());

            String clientMessage = (String) inStream.readObject();

            System.out.println("client.Client message: " + clientMessage);

            while(!flag) {
                    String actionCode = (String) inStream.readObject();

                    System.out.println("client.Client message: " + actionCode);

                    switch(actionCode) {
                        case "1":
                            login();
                            break;
                        case "2":
                            signup();
                            break;
                        case "3":
                            flag = true;
                            break;
                    }
                }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        
    }

    private void login() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();
        clientId = usersLoader.checkUser(userName,password);

        if(clientId != null){
            outStream.writeObject("Success");
            outStream.flush();
        }
        else {
            outStream.writeObject("Failed");
            outStream.flush();
        } 
    }

    private void signup() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        if(usersLoader.getUserInfo(userName).isEmpty()){
            clientId = Integer.toString((int)(Math.random() * 101));

            String formattedInfo = userName + ":" + password + "," + clientId;
            usersLoader.addUser(formattedInfo);
            outStream.writeObject("Success");
            outStream.flush();
        } 
        else {
            outStream.writeObject("Failed");
            outStream.flush();
        }
    }
}