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

        if(uploadHandshake()) {
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

            // First byte = description length
            int descLength = fullData[0] & 0xFF;  // unsigned byte to int

            byte[] descriptionBytes = new byte[descLength];
            System.arraycopy(fullData, 1, descriptionBytes, 0, descLength);
            String description = new String(descriptionBytes);

            byte[] imageBytes = new byte[fullData.length - 1 - descLength];
            System.arraycopy(fullData, 1 + descLength, imageBytes, 0, imageBytes.length);

            // Save image
            FileOutputStream fos = new FileOutputStream("received_image.jpg");
            fos.write(imageBytes);
            fos.close();

        }else{
            System.out.println("Hanshake Failed! Try again :(");
        }
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

    private boolean uploadHandshake() throws IOException, ClassNotFoundException {
        String handshakeResponse = (String) inStream.readObject();
        if(handshakeResponse.equals("request to upload")){
            outStream.writeObject("accepted");
            return true;
        }else{
            outStream.writeObject("rejected");
            return false;
        }
    }
}