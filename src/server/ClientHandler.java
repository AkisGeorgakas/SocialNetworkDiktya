package server;

import common.Packet;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.*;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private UsersLoader usersLoader = new UsersLoader("../data/users.txt");

    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;

    private boolean loginFlag = false;
    private boolean menuFlag = false;

    private String clientId;
    private final String GroupId = "45";

    public ClientHandler(Socket socket) throws IOException {
        this.clientSocket = socket;
    }

    public void run() {     
        try {
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());

            // connection message
            String clientMessage = (String) inStream.readObject();
            System.out.println("client.Client message: " + clientMessage);

            // login
            while(!loginFlag) {

              // user login menu Selection
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
                  // Exit
                  loginFlag = true;
                  break;

              }

            }

            // menu
            while(!menuFlag) {
                String actionCode = (String) inStream.readObject();

                System.out.println("client.Client message: " + actionCode);

                switch(actionCode) {
                    case "1":
                        handleUpload();
                        break;
                    case "2":
                        handleSearch();
                        break;
                    case "3":
                        menuFlag = true;
                        break;
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        
    }

    // Menu option 1
    private void handleUpload() throws IOException, ClassNotFoundException {

        if(uploadHandshake()) {
            System.out.println("upload sequence initiated");
            Map<Integer, byte[]> receivedPackets = new TreeMap<>();

            String imgNameGiven = (String) inStream.readObject();
            String[] imgNameArray = imgNameGiven.split("\\.");

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
            FileOutputStream fos = new FileOutputStream("server/directories/"+ "directory_" + GroupId + clientId + "/" + imgNameGiven);

            // Create txt with description given
            File file = new File("server/directories/"+ "directory_" + GroupId + clientId + "/" + imgNameArray[0] + ".txt");
            file.createNewFile();
            FileWriter fw = new FileWriter(file);
            fw.write(description + " " + imgNameGiven);
            fw.close();

            // update profile.txt
            FileWriter proFileServerWriter = new FileWriter("server/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
            proFileServerWriter.append("\n");
            proFileServerWriter.append(clientId + " posted " + imgNameGiven);
            proFileServerWriter.close();


            fos.write(imageBytes);
            fos.close();


        }else{
            System.out.println("Hanshake Failed! Try again :(");
        }
    }


    // Menu Option 2
    private void handleSearch() throws IOException, ClassNotFoundException{

      // read input from client
      String searcImgName = (String) inStream.readObject();

      SocialGraphLoader socialLoader = new SocialGraphLoader();
      ArrayList<String> following = socialLoader.getFollowing(clientId);

      System.out.println("Following: " + following);

      ArrayList<String[]> results = new ArrayList<String[]>();
      boolean foundExactMatch = false;

      for(String clientId : following) {

        try{
          BufferedReader reader = new BufferedReader(new FileReader("server/profiles/Profile_"+ GroupId + clientId + ".txt"));
          if(reader != null) {
            String line;

            while ((line = reader.readLine()) != null) {

              String photoFullName = (line.split(" "))[2];
              if(photoFullName.contains(searcImgName)) {

                  for(String[] result : results){
                      if(result[2].equals(photoFullName)){
                          foundExactMatch = true;
                          break;
                      }
                  }

                  if(!foundExactMatch){
                      results.add(new String[]{clientId, usersLoader.getUserName(clientId), photoFullName});
                  }

                  foundExactMatch = false;
              }
            }
            reader.close();
          }

        }catch(Exception e) {
          System.out.println(e.getMessage());
        }
      }
        outStream.writeObject(results);

        // int selectedImage =  Integer.parseInt((String)(inStream.readObject()));

        handleDownload(results);
    }

    private void handleDownload(ArrayList<String[]> imageInfo) throws ClassNotFoundException, IOException{
      if(downloadHandshake()) {
        System.out.println("Download sequence initiated");

        // read user selection from client
        String userSelection = (String) inStream.readObject();

        String imageName = imageInfo.get(Integer.parseInt(userSelection))[2];
        String imageDirectory = "server/directories/"+ "directory_" + GroupId + imageInfo.get(Integer.parseInt(userSelection))[0] + "/" + imageName;
        Path imagePath = Paths.get(imageDirectory);

        Map<Integer, byte[]> receivedPackets = new TreeMap<>();

        byte[] imageBytes = Files.readAllBytes(imagePath);
        byte[] descriptionBytes = imageName.getBytes();
        int descLength = descriptionBytes.length;
        
        // Combine data
        byte[] fullData = new byte[1 + descLength + imageBytes.length];

        // Divide into 10 packets
        int packetSize = (int) Math.ceil(fullData.length / 10.0);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        for (int i = 0; i < 10; i++) {
          int start = i * packetSize;
          int end = Math.min(start + packetSize, fullData.length);
          byte[] chunk = new byte[end - start];
          System.arraycopy(fullData, start, chunk, 0, end - start);

          // Send packet
          Packet packet = new Packet(i, chunk);
          outStream.writeObject(packet);
          outStream.flush();

          // Wait for ACK with timeout
          Future<String> future = executor.submit(() -> inStream.readLine());
          try {
            String ack = future.get(3, TimeUnit.SECONDS); // Timeout set to 3 seconds
            if (!ack.equals("ACK" + i)) {
                System.out.println("ACK mismatch. Resending...");
                i--; // resend
            } else {
                System.out.println("Received: " + ack);
            }
          } catch (TimeoutException e) {
              System.out.println("ACK timeout. Server did not receive ACK. Resending...");
              future.cancel(true); // cancel the task
              i--; // resend
          } catch (Exception e) {
              e.printStackTrace();
              break; // optional: break on unexpected exception
          }

          // Wait for ACK
          // String ack = inStream.readLine();
          // if (!ack.equals("ACK" + i)) {
          //   System.out.println("ACK mismatch or timeout. Resending...");
          //   i--; // resend
          // } else {
          //   System.out.println("Received: " + ack);
          // }
        }

        // update profile.txt
        FileWriter proFileServerWriter = new FileWriter("server/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(clientId + " reposted " + imageName);
        proFileServerWriter.close();


      }else{
          System.out.println("\nDownload Hanshake Failed! Try again :(");
      }
    }

    private boolean downloadHandshake() throws IOException, ClassNotFoundException {
      String handshakeResponse = (String) inStream.readObject();
      if(handshakeResponse.equals("request to download")){
          outStream.writeObject("acceptedDownload");
          return true;
      }else{
          outStream.writeObject("rejected");
          return false;
      }
  }














    private void login() throws IOException, ClassNotFoundException {
      String userName = (String) inStream.readObject();
      String password = (String) inStream.readObject();
      clientId = usersLoader.checkUser(userName,password);

      if(clientId != null){
          outStream.writeObject("SuccessLogin");
          outStream.flush();

          outStream.writeObject(clientId);
          outStream.flush();
          loginFlag = true;
      }
      else {
          outStream.writeObject("FailedLogin");
          outStream.flush();

          // resetLogin
          this.login();
      } 
    }

    private void signup() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        if(usersLoader.getUserInfo(userName).isEmpty()){
            clientId = Integer.toString((int)(Math.random() * 101));

            String formattedInfo = userName + ":" + password + "," + clientId;
            usersLoader.addUser(formattedInfo);

            outStream.writeObject("SuccessSignUp");
            outStream.flush();

            outStream.writeObject(clientId);
            outStream.flush();
            
            loginFlag = true;
        } 
        else {
            outStream.writeObject("Failed");
            outStream.flush();

            // resetSignup
            this.signup();
        }
    }

    private boolean uploadHandshake() throws IOException, ClassNotFoundException {
        String handshakeResponse = (String) inStream.readObject();
        if(handshakeResponse.equals("request to upload")){
            outStream.writeObject("acceptedUpload");
            return true;
        }else{
            outStream.writeObject("rejected");
            return false;
        }
    }
}