package client;

import common.Packet;
import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Scanner;

public class Client {
    private Socket connection;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String serverIP;
    private int serverPort;
    private boolean flag = false;
    private boolean flag2 = false;
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

            while(!flag2) {
                System.out.println("Select action:");
                System.out.println("\n1) Upload\n2) Follow\n3) See your Social Graph");
                String actionCode = myObj.nextLine();
                while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3"))) {
                    System.out.println("Wrong Input");
                    actionCode = myObj.nextLine();
                }


                out.writeObject(actionCode);
                out.flush();
                System.out.println("egrapsa action code ston server");

                switch (actionCode) {
                    case "1":

                        uploadPic();
                        break;
                    case "2":

                        break;
                    case "3":

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

    private boolean uploadHandshake() throws IOException, ClassNotFoundException {
        out.writeObject("request to upload");
        String handshakeResponse = (String) in.readObject();
        return handshakeResponse.equals("accepted");
    }

    private void uploadPic() throws IOException, ClassNotFoundException {
        if(uploadHandshake()) {
            System.out.println("ksekinhsa upload");
            System.out.println("Enter filename please");
            String pathname = myObj.nextLine();
            // Load image and description
            String fullpathname = "client/directory/" + pathname;
            File imageFile = new File(fullpathname);
            System.out.println("new file");
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            System.out.println("bytes");
            System.out.println("Enter a description please");
            String description = myObj.nextLine();

            byte[] descriptionBytes = description.getBytes();
            int descLength = descriptionBytes.length;

            if (descLength > 255) {
                throw new IllegalArgumentException("Description too long for 1-byte length field");
            }


            // Combine data
            byte[] fullData = new byte[1 + descLength + imageBytes.length];
            fullData[0] = (byte) descLength;
            System.arraycopy(descriptionBytes, 0, fullData, 1, descLength);
            System.arraycopy(imageBytes, 0, fullData, 1 + descLength, imageBytes.length);

            // Divide into 10 packets
            int packetSize = (int) Math.ceil(fullData.length / 10.0);
            for (int i = 0; i < 10; i++) {
                int start = i * packetSize;
                int end = Math.min(start + packetSize, fullData.length);
                byte[] chunk = new byte[end - start];
                System.arraycopy(fullData, start, chunk, 0, end - start);

                // Send packet
                Packet packet = new Packet(i, chunk);
                out.writeObject(packet);
                out.flush();

                // Wait for ACK
                String ack = in.readLine();
                if (!ack.equals("ACK" + i)) {
                    System.out.println("ACK mismatch or timeout. Resending...");
                    i--; // resend
                } else {
                    System.out.println("Received: " + ack);
                }
            }
        }else{
            System.out.println("Hanshake Failed! Try again :(");
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
            flag = true;
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
            flag = true;
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client("localhost", 303);
        System.out.println("Established connection with server!");
        client.startConnection();
    }
}