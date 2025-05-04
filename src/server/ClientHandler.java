package server;

import common.Packet;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private ObjectInputStream inStream;
    private ObjectOutputStream outStream;
    private UsersLoader usersLoader = new UsersLoader("../data/users.txt");
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

            String clientMessage = (String) inStream.readObject();

            System.out.println("client.Client message: " + clientMessage);

            // login
            while(!loginFlag) {
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

      ArrayList<String> followingUsersImgesMatch = new ArrayList<String>();

      for(String s : following) {

        try{
          BufferedReader reader = new BufferedReader(new FileReader("Profile_"+ GroupId + s + ".txt"));
          if(reader != null) {
            String line;

            while ((line = reader.readLine()) != null) {

              String photoName = (line.split(" "))[2].split("\\.")[0];
              if(photoName.equals(searcImgName)) {
                followingUsersImgesMatch.add(s + " " + searcImgName);
              }
            }
            reader.close();
         
          }

        }catch(Exception e) {
          System.out.println(e.getMessage());
        }
        
      }


    }
















    private void login() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();
        clientId = usersLoader.checkUser(userName,password);

        if(clientId != null){
            outStream.writeObject("Success");
            outStream.flush();

            outStream.writeObject(clientId);
            outStream.flush();
            loginFlag = true;
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

            outStream.writeObject(clientId);
            outStream.flush();
            
            loginFlag = true;
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