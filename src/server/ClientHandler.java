package server;

import common.Packet;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.TreeMap;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private UsersLoader usersLoader = new UsersLoader("../data/users.txt");
    private boolean flag = false;
    private boolean flag2 = false;
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
            while(!flag2) {
                String actionCode = (String) inStream.readObject();

                System.out.println("client.Client message: " + actionCode);

                switch(actionCode) {
                    case "1":
                        handleUpload();
                        break;
                    case "2":

                        break;
                    case "3":
                        flag2 = true;
                        break;
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        
    }

    private void handleUpload() throws IOException, ClassNotFoundException {

        System.out.println("upload sequence initiated");
        Map<Integer, byte[]> receivedPackets = new TreeMap<>();

        for (int i = 0; i < 10; i++) {
            System.out.println("inside for");
            Packet packet = (Packet) inStream.readObject();
            System.out.println("read Object");
            System.out.println("Received packet #" + packet.sequenceNumber);
            receivedPackets.put(packet.sequenceNumber, packet.data);

            // Send ACK
            outStream.write(("ACK" + packet.sequenceNumber + "\n").getBytes());
            outStream.flush();
        }

        // Reconstruct the image and description
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (int i = 0; i < 10; i++) {
            combined.write(receivedPackets.get(i));
        }

        byte[] fullData = combined.toByteArray();

        // Extract description and image
        String fullString = new String(fullData);  // crude split if description size is known
        System.out.println("Received description + image bytes: " + fullData.length);

        FileOutputStream fos = new FileOutputStream("received_image.jpg");
        fos.write(fullData);  // In a real case, you'd split description/image properly
        fos.close();
    }

    private void login() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();
        clientId = usersLoader.checkUser(userName,password);

        if(clientId != null){
            outStream.writeObject("Success");
            outStream.flush();
            flag = true;
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
            flag = true;
        } 
        else {
            outStream.writeObject("Failed");
            outStream.flush();
        }
    }
}