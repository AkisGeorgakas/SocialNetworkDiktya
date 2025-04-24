package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private UsersLoader usersLoader = new UsersLoader("../data/users.txt");

    public ClientHandler(Socket socket) throws IOException {
        this.clientSocket = socket;
    }

    public void run() {        
        try {
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());

            String clientMessage = (String) inStream.readObject();

            System.out.println("client.Client message: " + clientMessage);

            String actionCode = (String) inStream.readObject();

            switch(actionCode) {
                case "1":
                    login();
                    break;
                case "2":
                    signup();
                    break;
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void login() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        if(usersLoader.checkUser(userName,password)){
            outStream.writeObject("Success");
            outStream.flush();
        }else{
            outStream.writeObject("Failed");
            outStream.flush();
        }



    }

    private void signup() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        if(usersLoader.getUserInfo(userName).isEmpty()){
            String formattedInfo = userName + ":" + password + "," + (int)(Math.random() * 101);
            usersLoader.addUser(formattedInfo);
            outStream.writeObject("Success");
            outStream.flush();
        }else{
            outStream.writeObject("Failed");
            outStream.flush();
        }
    }
}