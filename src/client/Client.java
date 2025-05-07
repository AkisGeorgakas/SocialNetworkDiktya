package client;

import common.Packet;
import server.SocialGraphLoader;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;

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

          out.writeObject("Established connection with server!");
          out.flush();

          while(!loginFlag) {
            
            System.out.println("**** Login Menu ****");
            System.out.println("\n1) Log In\n2) Sign Up\n3) Exit\n\nPlease insert a valid action number from 1-3 to continue:");
            String actionCode = myObj.nextLine();

            while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3"))) {
              System.out.println("Wrong Input! Please insert a valid action number from 1-3:");
              actionCode = myObj.nextLine();                    
            }

            // send client's action code to the server
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

            System.out.println("\n****Main Menu ****");
            System.out.println("\n1) Upload Image from Client to Server\n2) Search Image on server\n3) Follow\n4) See your Social Graph\n5) Exit");
            System.out.println("\nPlease insert a valid action number from 1-5 to continue:");
            String actionCode = myObj.nextLine();

            while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3") || Objects.equals(actionCode, "4") || Objects.equals(actionCode, "5"))) {
              System.out.println("Wrong Input!\nPlease insert a valid action number from 1-5 to continue:");
              actionCode = myObj.nextLine();
            }

            // send client's action code to the server
            out.writeObject(actionCode);
            out.flush();
            // System.out.println("egrapsa action code ston server");

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
        return handshakeResponse.equals("acceptedUpload");
    }

    private void uploadPic() throws IOException, ClassNotFoundException {
        if(uploadHandshake()) {
            // System.out.println("ksekinhsa upload");
            String pathname = "";

            // check input filename
            while(true){
              System.out.println("\nEnter filename please:");
              pathname = myObj.nextLine();  
              
              boolean imgTag = pathname.contains(".jpg") || pathname.contains(".png") || pathname.contains(".jpeg") || pathname.contains(".JPG") || pathname.contains(".PNG") || pathname.contains(".JPEG");

              if((pathname.split("\\.").length == 2) && imgTag){
                break;
              }else{
                System.out.println("\nWrong input! Please insert a valid filename:");
              }
            }

            // send server img name
            out.writeObject(pathname);

            // Load image and description
            String fullpathname = "client/directory/" + pathname;
            File imageFile = new File(fullpathname);

            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

            System.out.println("\nEnter a description please:");
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
            System.out.println("\nHanshake Failed! Try again :(");
        }
    }




    public void searchImg() throws IOException, ClassNotFoundException {

        String searchImgInput = "";

        // check input filename
        while(true){
            System.out.println("\nEnter file you want to search:");
            searchImgInput = myObj.nextLine();

            if(searchImgInput != null && searchImgInput.length() > 0){
                break;
            }else{
                System.out.println("\nWrong input! Please insert a valid filename:");
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
                System.out.println(counter++ + ". User: " + result[1] + " - Picture: " + result[2]);
            }

            System.out.println("Please select an image to download.(1-" + results.size() + ")");
            String userSelection = myObj.nextLine();
            while(true){

              if(userSelection != null && userSelection.matches("\\d+") && Integer.parseInt(userSelection) <= results.size()){
                break;
              }else{
                System.out.println("\nWrong input! Please insert and integer between 1 and " + results.size());
                userSelection = myObj.nextLine();
              }
            }
            
            // begin download process
            downloadPic(userSelection, results.get(Integer.parseInt(userSelection)));

        }else{
            System.out.println("No results found :(");

        }


    }

    private void downloadPic(String userSelection, String[] imageInfo) throws ClassNotFoundException, IOException{

      if(downloadHandshake()) {
        // send server user picture selection
        out.writeObject(userSelection);

        Map<Integer, byte[]> receivedPackets = new TreeMap<>();

        String imgNameGiven = imageInfo[2] ;

        // for the occasion of 9.e
        boolean firstTime3rdPackage = false;

        for (int i = 0; i < 10; i++) {


          // System.out.println("inside for");
          Packet packet = (Packet) in.readObject();
          // System.out.println("read Object");
          System.out.println("Received packet #" + packet.sequenceNumber);
          receivedPackets.put(packet.sequenceNumber, packet.data);

          
          // Send ACK
          // for the occasion of 9.e
          if(i == 2 && !firstTime3rdPackage){
            System.out.println("Didn't send package on porpuse");
            firstTime3rdPackage = true;
            i--;
          }else{
            out.write(("ACK" + packet.sequenceNumber + "\n").getBytes());
            out.flush();
          }
          
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
        FileOutputStream fos = new FileOutputStream("client/directory/" + imgNameGiven);

        // Create txt with description given
        File file = new File("client/directory/" + imageInfo[2].split("\\.")[0] + ".txt");
        file.createNewFile();
        FileWriter fw = new FileWriter(file);
        fw.write(description + " " + imgNameGiven);
        fw.close();

        // update profile.txt
        FileWriter proFileServerWriter = new FileWriter("client/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(clientId + " posted " + imgNameGiven);
        proFileServerWriter.close();

        fos.write(imageBytes);
        fos.close();        

        // 9.h)
        System.out.println("The transmission is completed!");
      }
    }

    private boolean downloadHandshake() throws IOException, ClassNotFoundException {
      out.writeObject("request to download");
      String handshakeResponse = (String) in.readObject();
      return handshakeResponse.equals("acceptedDownload");
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

      System.out.println("Username:");
      String userName = myObj.nextLine();

      System.out.println("Password:");
      String password = myObj.nextLine();

      out.writeObject(userName);
      out.flush();

      out.writeObject(password);
      out.flush();

      // server response for login
      String response = (String) in.readObject();

      if(response.equals("SuccessLogin")){
        
        // update local clientId variable
        clientId = (String) in.readObject();

        System.out.println("\nSuccessful login!\n");
        loginFlag = true;

      }else{

        System.out.println("\nFailed login! Username or password is incorrect.\nPlease try again.\n");
        this.login();
      }

    }

    public void signup() throws IOException, ClassNotFoundException {

        // todo direcories
        // todo create profiles 
        System.out.println("Create a username:");
        String userName = myObj.nextLine();

        System.out.println("Create a password:");
        String password = myObj.nextLine();

        out.writeObject(userName);
        out.flush();

        out.writeObject(password);
        out.flush();

        String response = (String) in.readObject();
        if(response.equals("SuccessSignUp")){

            clientId = (String) in.readObject();

            System.out.println("\nSuccessful sign up!\nYou are now logged in.\n");
            loginFlag = true;

        }else{
          System.out.println("\nFailed to sign up! Username already exists.\nPlease try different username.\n");
          this.signup();
        }
    }
    
    public static void main(String[] args) {
        Client client = new Client("localhost", 303);
        System.out.println("\nEstablished connection with server!\n");
        client.startConnection();
    }
}