package client;

import common.Packet;
import server.SocialGraphLoader;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class Client {

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private Socket connection;
    private String serverIP;
    private int serverPort;

    private boolean loginFlag = false;
    private boolean menuFlag = false;

    private Scanner myObj = new Scanner(System.in);

    private final String GroupId = "45";

    private String clientId;

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

            while(!loginFlag) {
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

            while(!menuFlag) {
                System.out.println("Select action:");
                System.out.println("\n1) Upload Image from Client to Server\n2) Search Image on server\n3) Follow\n4) See your Social Graph\n5) Exit");
                String actionCode = myObj.nextLine();
                while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3") || Objects.equals(actionCode, "4") || Objects.equals(actionCode, "5"))) {
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
                        //Search Image
                        searchImg();
                        break;
                    case "3":
                        //Follow
                        break;
                    case "4":
                        //See your Social Graph
                    break;

                    case "5":
                        //Exit
                        menuFlag = true;
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
            String pathname = "";

            // check input filename
            while(true){
              System.out.println("Enter filename please:");
              pathname = myObj.nextLine();  
              
              boolean imgTag = pathname.contains(".jpg") || pathname.contains(".png") || pathname.contains(".jpeg") || pathname.contains(".JPG") || pathname.contains(".PNG") || pathname.contains(".JPEG");

              if((pathname.split("\\.").length == 2) && imgTag){
                break;
              }else{
                System.out.println("Wrong input!");
              }
            }

            // send server img name
            out.writeObject(pathname);

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

            // update profile.txt
            FileWriter proFileServerWriter = new FileWriter("client/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
            proFileServerWriter.append("\n");
            proFileServerWriter.append(clientId + " posted " + pathname);
            proFileServerWriter.close();
        }else{
            System.out.println("Hanshake Failed! Try again :(");
        }
    }

    public void searchImg() throws IOException, ClassNotFoundException {

        String searchImgInput = "";

        // check input filename
        while(true){
            System.out.println("Enter file you want to search:");
            searchImgInput = myObj.nextLine();

            if(searchImgInput != null && searchImgInput.length() > 0){
                break;
            }else{
                System.out.println("Wrong input!");
            }
        }

        // send client's keyword to the server
        //out = new ObjectOutputStream(connection.getOutputStream());
        out.writeObject(searchImgInput);
        out.flush();

        ArrayList<String[]> results = (ArrayList<String[]>)in.readObject();
        int counter = 1;

        if(!results.isEmpty()){
            System.out.println("Found the following images:");
            for(String[] result: results){
                System.out.println(counter++ + ". " + result[1]);
            }
            System.out.println("Please select an image to download.(1-" + results.size() + ")");
            String userSelection = myObj.nextLine();

            out.writeObject(userSelection);
            downloadPic();

        }else{
            System.out.println("No results found :(");

        }


    }

    private void downloadPic(){

        System.out.println("Download sequence will start");
    }











    public void stop() {
        try {
            in.close();
            out.close();
            connection.close();
            loginFlag = true;

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
          
            clientId = (String) in.readObject();

            System.out.println("Mpravo sou");
            loginFlag = true;
        }

    }

    public void signup() throws IOException, ClassNotFoundException {

        // todo direcories
        // todo create profiles 
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

            clientId = (String) in.readObject();

            System.out.println("Mpravo sou");
            loginFlag = true;
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client("localhost", 303);
        System.out.println("Established connection with server!");
        client.startConnection();
    }
}