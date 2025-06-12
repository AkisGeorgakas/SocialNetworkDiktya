package server;

import common.Packet;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler extends Thread {
  
  // Sockets
  private final Socket clientSocket;

  // Loaders
  private final UsersLoader usersLoader = new UsersLoader("../data/users.txt");
  SocialGraphLoader socialLoader = new SocialGraphLoader();

  // Streams
  private ObjectInputStream inStream;
  private ObjectOutputStream outStream;

  // Menu Flags
  private boolean loginFlag = false;
  private boolean menuFlag = false;

  // Client ID
  private String clientId;

  // Group ID
  private final String GroupId = "45";

  // Map with locks per file
  private static final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

  // Constructor
  public ClientHandler(Socket socket) throws IOException {
    this.clientSocket = socket;
  }

  public void run() {

    try {
      // Streams
      outStream = new ObjectOutputStream(clientSocket.getOutputStream());
      inStream = new ObjectInputStream(clientSocket.getInputStream());

      // connection message
      String clientMessage = (String) inStream.readObject();
      System.out.println("client.Client message: " + clientMessage);

      // Login Menu
      while (!loginFlag) {

        // user login menu Selection
        String actionCode = (String) inStream.readObject();
        System.out.println("client.Client message: " + actionCode);

        switch (actionCode) {

          case "1":
            login();
            break;

          case "2":
            signup();
            break;

          case "3":
            // Exit
            this.stopClientHandler();
            break;

        }

      }

        // Main Menu
        while (!menuFlag) {

            // Read menu action
            String actionCode = (String) inStream.readObject();
            System.out.println("client.Client message: " + actionCode);

            // TODO Add comment functionality
            // TODO Add ask-comment functionality
            // TODO Add ask-download functionality
            // TODO Add permit-download functionality
            switch (actionCode) {

                case "1":
                    handleUpload();
                    break;

                case "2":
                    handleSearch();
                    break;

                case "3":
                    handleFollow();
                    break;

                case "4":
                    handleUnfollow();
                    break;

                case "5":
                    handleAccessProfile();
                    break;

                case "6":
                    //Exit
                    this.stopClientHandler();
                    break;

                default:
                    System.out.println("Wrong input for menu action!");
                    break;
            }
        }

    } catch (IOException | ClassNotFoundException | InterruptedException e) {
      e.printStackTrace();
    }

  }




  // LOGIN MENU FUNCTIONS ------------------------------------------------------------------------------------------------------------------------------------

  private void login() throws IOException, ClassNotFoundException {

    // Read username and password
    String userName = (String) inStream.readObject();
    String password = (String) inStream.readObject();

    // Search for client's ID and succcessfully login, from users.txt using username and password
    clientId = usersLoader.checkUser(userName, password);

    if (clientId != null) {
    
      // Send success login response
      outStream.writeObject("SuccessLogin");
      outStream.flush();

      // Send client ID
      outStream.writeObject(clientId);
      outStream.flush();

      // stop login menu
      loginFlag = true;

      // Save client's IP and port to server
      Server.clientDirectory.put(clientId, clientSocket.getRemoteSocketAddress());
      System.out.println("\nOnline clients: ");
      Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));
      System.out.println("");

      updateClientsDirecotryFiles();

      updateClientProfileFiles();

      checkNotifications();
        
    } else {
      outStream.writeObject("FailedLogin");
      outStream.flush();

      // resetLogin to retry
      this.login();
    }
  }

  private void signup() throws IOException, ClassNotFoundException {
    // Read username and password
    String userName = (String) inStream.readObject();
    String password = (String) inStream.readObject();
    String language = (String) inStream.readObject();

    // Check if username already exists
    if (usersLoader.getUserInfo(userName).isEmpty()) {

      // Generate random client ID and check to be unique
      String tempId = Integer.toString((int) (Math.random() * 101));
      while (usersLoader.getUserName(tempId) != "") {
        tempId = Integer.toString((int) (Math.random() * 101));
      }

      clientId = tempId;

      // Add user to users.txt
      String formattedInfo = userName + ":" + password + "," + clientId + "," + language;
      usersLoader.addUser(formattedInfo);

      // Send success signup response
      outStream.writeObject("SuccessSignUp");
      outStream.flush();

      // Send client ID
      outStream.writeObject(clientId);
      outStream.flush();



      // Save client's IP and port to server
      Server.clientDirectory.put(clientId, clientSocket.getRemoteSocketAddress());
      System.out.println("\nOnline clients: ");
      Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));
      System.out.println("");      

      fixNewUserlFiles();

      // Stop login menu
      loginFlag = true;

    } else {
      // Send failed signup response
      outStream.writeObject("Failed");
      outStream.flush();

      // resetSignup
      this.signup();
    }
  }


  // ---------------------------------------------------------------------------------------------------------------------------------------------------------




  // MAIN MENU -----------------------------------------------------------------------------------------------------------------------------------------------

  // Menu option 1
  private void handleUpload() throws IOException, ClassNotFoundException {

    // Check handshake
    if (uploadHandshake()) {

      System.out.println("Upload sequence initiated");
      Map<Integer, byte[]> receivedPackets = new TreeMap<>();

      String imgNameGiven = (String) inStream.readObject();
      System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(filename)");
      String[] imgNameArray = imgNameGiven.split("\\.");


      // send client preffered language
      String languageShort = usersLoader.getUsersLanguage(clientId);
      outStream.writeObject(languageShort);
      String languagePref = "";
      String secondLanguagePref = "";
      switch (languageShort) {
        case "gr":
          languagePref = "Greek";
          secondLanguagePref = "English";
          break;
        case "en":
          languagePref = "English";
          secondLanguagePref = "Greek";
          break;

        default:
          languagePref = "error lang " + languageShort;
          break;
      }

      String description1 = (String) inStream.readObject();
      String description2 = (String) inStream.readObject();

      // Receive 10 packets
      for (int i = 0; i < 10; i++) {
        Packet packet = (Packet) inStream.readObject();
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
      FileOutputStream fos = new FileOutputStream("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameGiven);

      // Create txt with description given
      File file = new File("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameArray[0] + ".txt");
      if ( !file.createNewFile() ) {
          System.out.println("File already exists.");
      }

      // FileWriter fw = new FileWriter(file);
      // fw.write(description + " " + imgNameGiven);
      // fw.close();
      FileWriter fw = new FileWriter(file);
      fw.write(languagePref + " " +  description1 + " " + imgNameGiven);
      if(description2.length() > 0){
        fw.write("\n" + secondLanguagePref + " " +  description2 + " " + imgNameGiven);
      }
      fw.close();

      String profileTxtpath = "server/profiles/" + "Profile_" + GroupId + clientId + ".txt";

      // lock profile.txt to prevent other clients from editing
      lockFile(profileTxtpath);
      // update profile.txt
      FileWriter proFileServerWriter = new FileWriter(profileTxtpath, true);
      proFileServerWriter.append("\n");
      proFileServerWriter.append(clientId).append(" posted ").append(imgNameGiven);
      proFileServerWriter.close();
      // unlock profile.txt
      unlockFile(profileTxtpath);


      String[] following = socialLoader.getFollowers(clientId);
      for (String myFollowing : following) {

        String otherTxtpath = "server/profiles/" + "Others_" + GroupId + myFollowing + ".txt";
        // lock others.txt to prevent other clients from editing
        lockFile(otherTxtpath);
        // update others.txt
        FileWriter proFileServerWriter2 = new FileWriter(otherTxtpath, true);
        proFileServerWriter2.append("\n");
        proFileServerWriter2.append(clientId).append(" posted ").append(imgNameGiven);
        proFileServerWriter2.close();
        // unlock others.txt
        unlockFile(otherTxtpath);
      }

      // Write image
      fos.write(imageBytes);
      fos.close();


    } else {
      System.out.println("Hanshake Failed! Try again :(");
    }

  }

  // Handshake for upload
  private boolean uploadHandshake() throws IOException, ClassNotFoundException {

    String handshakeResponse = (String) inStream.readObject();

    System.out.println("\nHANDSHAKE STEP 1: Client sent request");

    if (handshakeResponse.equals("Request to upload")) {

      outStream.writeObject("acceptedUpload");
      System.out.println("\nHANDSHAKE STEP 2: Server sent acknowledgement");

      return true;

    } else {

      System.out.println("\nHANDSHAKE REJECTED");
      outStream.writeObject("rejected");

      return false;

    }
  }

  // Menu Option 2
  private void handleSearch() throws IOException, ClassNotFoundException, InterruptedException {

    // read input image name from client
    String searchImgName = (String) inStream.readObject();

    // read input language from client
    String searchImgLang = (String) inStream.readObject();

    ArrayList<String> following = socialLoader.getFollowing(clientId);

    ArrayList<String[]> results = new ArrayList<String[]>();
    boolean foundExactMatch = false;


    String profileTxtpath;
    for (String tempclientId : following) {
        profileTxtpath = "server/profiles/Profile_" + GroupId + tempclientId + ".txt";

        try {
            
            lockFile(profileTxtpath);

            BufferedReader reader = new BufferedReader(new FileReader(profileTxtpath));
            String line;

            // FOR TEST PURPOSES WE LEAVE THIS TO CHECK LOCKED FILE
            // if(this.tempclientId.equals("9432")) {
            //   sleep(10000);
            // }
            

            while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {

              String[] parts = line.split("\\s+");
              if (parts.length >= 3) {
                String photoFullName = line.split("\\s+")[2];
                if (photoFullName.trim().toLowerCase().contains(searchImgName.trim().toLowerCase())) {
                  // filter image found by language
                  String txtPath = "server/directories/directory_" + GroupId + tempclientId + "/" + photoFullName.split("\\.")[0] + ".txt";
                  lockFile(txtPath);
                  BufferedReader reader2 = new BufferedReader(new FileReader(txtPath));
                  String txtline;
                  boolean foundTxtWithCorrectLanguage = false;
                  while ((txtline = reader2.readLine()) != null) {
                    if(txtline.contains(searchImgLang)) {
                      foundTxtWithCorrectLanguage = true;
                      break;
                    }
                  }
                  unlockFile(txtPath);
                  reader2.close();
                  // --

                  if(foundTxtWithCorrectLanguage){
                    for (String[] result : results) {
                        if (result[2].equals(photoFullName)) {
                            foundExactMatch = true;
                            break;
                        }
                    }

                    if (!foundExactMatch) {
                        results.add(new String[]{tempclientId, usersLoader.getUserName(tempclientId), photoFullName});
                    }

                    foundExactMatch = false;
                  }
                }
              }
            }
            reader.close();

            unlockFile(profileTxtpath);

        } catch (Exception e) {
            unlockFile(profileTxtpath);
            System.out.println(e.getMessage());
        }
    }
    outStream.writeObject(results);

    if(!results.isEmpty()){
      handleDownload(results);
    }
    
  }

  // TODO Change to GoBackN protocol
  private void handleDownload(ArrayList<String[]> imageInfo) throws ClassNotFoundException, IOException, InterruptedException {
      if (downloadHandshake()) {
          System.out.println("Download sequence initiated");

          // read user selection from client
          int userSelectionNum = (int) inStream.readObject();
          System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(user selection)");

          String imageName = imageInfo.get(userSelectionNum)[2];
          String descriptionName = imageName.split("\\.")[0] + ".txt";
          downloadSomething(imageName, descriptionName, imageInfo.get(userSelectionNum)[0]);


          lockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");

          // update profile.txt
          FileWriter proFileServerWriter = new FileWriter("server/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
          proFileServerWriter.append("\n");
          proFileServerWriter.append(clientId).append(" reposted ").append(imageName);
          proFileServerWriter.close();

          unlockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");

          // 9.h ------
          String[] filesToCopy = {
                  imageName,
                  descriptionName
          };

          // making directories
          String sourceDir = "server/directories/" + "directory_" + GroupId + imageInfo.get(userSelectionNum)[0];
          String targetDir = "server/directories/" + "directory_" + GroupId + clientId;

          this.copyFiles(sourceDir, targetDir, filesToCopy);
          // 9.h ------

      } else {
          System.out.println("\nDownload Hanshake Failed! Try again :(");
      }
  }

  // handshake for download
  private boolean downloadHandshake() throws IOException, ClassNotFoundException {
      String handshakeResponse = (String) inStream.readObject();
      System.out.println("HANDSHAKE STEP 1: Client sent request");
      if (handshakeResponse.equals("request to download")) {
          outStream.writeObject("acceptedDownload");
          System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");
          return true;
      } else {
          outStream.writeObject("rejected");
          return false;
      }
  }

  // 9.h
  private void copyFiles(String sourceDest, String targetDest, String[] filesToCopyArr) {
      Path sourceDir = Paths.get(sourceDest);
      Path targetDir = Paths.get(targetDest);

      for (String fileName : filesToCopyArr) {
          Path sourceFile = sourceDir.resolve(fileName);
          Path targetFile = targetDir.resolve(fileName);
          try {
              Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
              System.out.println("\n" + "Copied: " + sourceFile + " to " + targetFile + "\n");
          } catch (IOException e) {
              System.err.println("\n" + "Failed to copy " + fileName + ": " + e.getMessage() + "\n");
          }
      }
  }




  // Menu option 3
  private void handleFollow() throws IOException, ClassNotFoundException {
      String response = "";
      // read input from client
      String userToFollow = (String) inStream.readObject();
      String userToFollowId = usersLoader.getUserId(userToFollow);
      if (userToFollowId.isEmpty()){
          response = "User not found! Try again.";
      }else{
          ArrayList<String> userIdStructure = new ArrayList<String>();
          userIdStructure.add(userToFollowId);
          sendFollowRequests(clientId,userIdStructure);
          response = "Follow request sent successfully!";
      }
      outStream.writeObject(response);

  }

  private void sendFollowRequests(String clientId, ArrayList<String> sendTo){
      for (String sendToId : sendTo){
          String filePath = "server/directories/directory_" + GroupId + sendToId + "/notifications.txt";
          try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
              writer.write(clientId);
              writer.newLine();
          } catch (IOException e) {
              System.err.println("Failed to write to " + filePath);
          }
      }
  }

  // Menu option 4
  private void handleUnfollow() throws ClassNotFoundException, IOException{
      String response = "";

      // read input from client
      String userToUnFollow = (String) inStream.readObject();
      String userToUnFollowId = usersLoader.getUserId(userToUnFollow);

      if (userToUnFollowId.isEmpty()){
          response = "The user you are trying to unfollow does not exist! Try again.";
      }else{
          response = socialLoader.unfollowUser(clientId, userToUnFollowId);
          System.out.println("User " + clientId + " successfully unfollowed user " + userToUnFollow);
      }
      outStream.writeObject(response);
  }

    // Menu option 5
    // Handle Access Profile
    private void handleAccessProfile() throws IOException, ClassNotFoundException, InterruptedException {
        boolean flag = true;

        while (flag) {
            List<String> usersList = new ArrayList<>(usersLoader.getAllUsers());

            // Send to client a list of all usernames on users.txt
            outStream.writeObject(usersList);
            outStream.flush();

            // Read the profile name client wants to access
            String profileName = (String) inStream.readObject(); // ProfileName, client requested access

            System.out.println("Client wants to access profile " + profileName);

            ArrayList<String> following = socialLoader.getFollowing(clientId);
            String selectedID = usersLoader.getUserInfo(profileName).get(1); // ProfileID, client requested access

            if (following.contains(selectedID)) {
                outStream.writeObject("access_approved");
                outStream.flush();

                //File profileFile = new File("server/profiles/Profile_" + GroupId + selectedID);

                String content = Files.readString(Paths.get("server/profiles/Profile_" + GroupId + selectedID + ".txt"));
                outStream.writeObject(content);
                outStream.flush();

                BufferedReader reader = new BufferedReader(new FileReader("server/profiles/Profile_" + GroupId + selectedID + ".txt"));
                String line;

                // Keep all available images to download from profile
                ArrayList<String[]> imgsToDownload = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    ArrayList<String> splitLine = new ArrayList<String>(Arrays.asList(line.split("\\s+")));

                    if (splitLine.size() == 3) {
                        if (splitLine.get(1).equals("posted") || splitLine.get(1).equals("reposted")) {
                            imgsToDownload.add(new String[]{selectedID, profileName, splitLine.get(2)});
                        }
                    }
                }

                // Send img list to client
                outStream.writeObject(imgsToDownload);
                outStream.flush();

                handleDownload(imgsToDownload);

            } else {
                outStream.writeObject("access_denied");
                outStream.flush();

                if (((String) inStream.readObject()).equals("no_retry")) flag = false;
            }
        }
    }

  // ---------------------------------------------------------------------------------------------------------------------------------------------------------





  // GENERAL FUNCTIONS -------------------------------------------------------------------------------------------------------------------

  // Sync all files from server to client
  private void updateClientsDirecotryFiles() throws IOException {

    System.out.println("Synchronizing files with client...");

    File directoryFolder = new File("server/directories/directory_" + GroupId + clientId);
    File[] files = directoryFolder.listFiles();

    boolean imgTag = false;
    String fileName = "";
    String descriptionName = "";

    if (files != null) {

      for (File file : files) {
        fileName = file.getName();
        imgTag = fileName.contains(".jpg") || fileName.contains(".png") || fileName.contains(".jpeg") || fileName.contains(".JPG") || fileName.contains(".PNG") || fileName.contains(".JPEG");

        if (imgTag) {

          outStream.writeObject(fileName);
          descriptionName = fileName.split("//.")[0] + ".txt";
          downloadSomething(fileName, descriptionName, clientId);

        }

      }

      // Send DONE
      outStream.writeObject("DONE");

    } else {
      outStream.writeObject("NotFound");
    }

  }

  // Sign out client and terminate everything necessary
  public void stopClientHandler() {

    try {
      // Remove client from online clients in Server
      Server.clientDirectory.remove(clientId);
      System.out.println("\nOnline clients: ");
      Server.clientDirectory.forEach((key, value) -> System.out.println(usersLoader.getUserName(key) + " " +  key + " " + value));
      System.out.println("\n");

      loginFlag = true;
      menuFlag = true;
      inStream.close();
      outStream.close();

      System.out.println("Connection closed");

    } catch (IOException e) {
      System.out.println(e.getMessage());
    }

  }

  // Check notifications in notifications.txt after successful login
  private void checkNotifications() throws IOException, ClassNotFoundException {

    ArrayList<String> notifications = new ArrayList<String>();
    String notificationsPath = "server/directories/directory_" + GroupId + clientId + "/notifications.txt";

    try (BufferedReader reader = new BufferedReader(new FileReader(notificationsPath))) {

      String line;
      while ((line = reader.readLine()) != null) {
        if(!line.trim().equals("")){
          notifications.add(line);
        }
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // send notifications to client
    outStream.writeObject(notifications);

    //All notifications have been read. We empty the notifications.txt file
    try (FileWriter writer = new FileWriter(notificationsPath, false)) {
        // Nothing to write – this will truncate the file to zero length
        // writer.close();
    } catch (IOException e) {
        e.printStackTrace();
    }


    ArrayList<String> responses = new ArrayList<String>();

    if (!notifications.isEmpty()) {

      for (String notification : notifications) {
          responses.add((String) inStream.readObject());
      }

      String[] splitResponse = {"", ""};
      ArrayList<String> acceptFrom = new ArrayList<String>();
      ArrayList<String> sendTo = new ArrayList<String>();

      for (String response : responses) {

        splitResponse = response.split("\\s+");
        switch (splitResponse[0]) {

          case "1":
            System.out.println("Client " + clientId + " accepted follow request from " + splitResponse[1]);
            acceptFrom.add(splitResponse[1]);
            break;
              
          case "2":
            //reject
            System.out.println("Client " + clientId + " rejected follow request from " + splitResponse[1]);
            break;

          case "3":
            System.out.println("Client " + clientId + " accepted and sent back follow request from " + splitResponse[1]);
            acceptFrom.add(splitResponse[1]);
            sendTo.add(splitResponse[1]);
            break;
        }
      }

      socialLoader.acceptFollowRequests(clientId,acceptFrom);
      sendFollowRequests(clientId,sendTo);
      responses.clear();
    }

  }

  // General funtion to handle download from server to client
  private void downloadSomething(String imageName, String descriptionName, String userId) throws IOException {

    // directories
    String imageDirectory = "server/directories/" + "directory_" + GroupId + userId + "/" + imageName;
    String descriptionDirectory = "server/directories/" + "directory_" + GroupId + userId + "/" + descriptionName;

    Path imagePath = Paths.get(imageDirectory);

    String descriptionLine = "";
    byte[] descriptionBytes = new byte[0];
    int descriptionLength = 0;

    byte[] imageBytes = Files.readAllBytes(imagePath);


    // try catch to check if bind txt exists
    try {
      BufferedReader reader = new BufferedReader(new FileReader(descriptionDirectory));
      descriptionLine = reader.readLine();
      reader.close();
      outStream.writeObject("Selected Picture has bind .txt file.");

    } catch (Exception e) {

      // 9.g
      outStream.writeObject("Selected Picture didn't have bind .txt file.");
    }


    descriptionBytes = descriptionLine.getBytes();
    descriptionLength = descriptionBytes.length;

    // Combine data
    byte[] fullData = new byte[1 + descriptionLength + imageBytes.length];
    fullData[0] = (byte) descriptionLength;
    System.arraycopy(descriptionBytes, 0, fullData, 1, descriptionLength);
    System.arraycopy(imageBytes, 0, fullData, 1 + descriptionLength, imageBytes.length);

    // Divide into 10 packets
    int packetSize = (int) Math.ceil(fullData.length / 10.0);
    ArrayList<String> receivedAcknowledgements = new ArrayList<String>();
    boolean ignoreNext = false;

    for (int i = 0; i < 10; i++) {

        if (!ignoreNext) {
          int start = i * packetSize;
          int end = Math.min(start + packetSize, fullData.length);
          byte[] chunk = new byte[end - start];
          System.arraycopy(fullData, start, chunk, 0, chunk.length);

          // Send the packet
          Packet packet = new Packet(i, chunk);
          outStream.writeObject(packet);
          outStream.flush();

          System.out.println("Sent packet " + i);

        } else {
            ignoreNext = false;
        }

        // Set temporary timeout for ACK
        int originalTimeout = clientSocket.getSoTimeout();
        try {

            clientSocket.setSoTimeout(3000); // 3-second timeout

            Object ackObj = inStream.readObject();

            if (ackObj instanceof String ack && ack.equals("ACK" + i) && !receivedAcknowledgements.contains(ack)) {
              System.out.println("Received: " + ack);
              receivedAcknowledgements.add(ack);

            } else {

              if (i != 9) {

                // resend
                if (ackObj instanceof String ack && receivedAcknowledgements.contains(ack)) {
                    System.out.println("I received a duplicate ACK. Ignored. -> " + ack);
                    ignoreNext = true;
                } else {
                    System.out.println("Invalid ACK. Resending packet " + i);
                }
                i--; //run again and don't send a new packet

              } else {
                break;
              }
            }

        } catch (SocketTimeoutException e) {
          // 9.e message
          System.out.println("Server did not receive ACK" + i + ". Resending...");
          i--; // resend

        } catch (Exception e) {
          e.printStackTrace();
          break;

        } finally {
          try {
              clientSocket.setSoTimeout(originalTimeout); // Restore timeout
          } catch (SocketException e) {
              e.printStackTrace();
          }
        }
    }

    // Print received acknowledgements array
    System.out.println("Received Acknowledgements: "+receivedAcknowledgements);
  }  

  // Locks selected file
  private void lockFile(String filePath) {

    fileLocks.putIfAbsent(filePath, new ReentrantLock());
    ReentrantLock lock = fileLocks.get(filePath);

    while (true) {
      
      if (lock.tryLock()) {
        System.out.println("Client " + clientId + ": granted access to " + filePath);
        break;

      } else {

        System.out.println("Client " + clientId + ": Trying to access file: " + filePath + " but is locked. Waiting...");

        try {
          Thread.sleep(2000); // 2 Seconds before trying again

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }

      }
    }
  }

  // Unlocks the selected file
  private void unlockFile(String filePath) {

    ReentrantLock lock = fileLocks.get(filePath);
    
    // Check if the file is locked or already unlocked
    if (lock != null && lock.isHeldByCurrentThread()) {
      lock.unlock();
      System.out.println("File Unlocked: " + filePath);
    }

  }


  private void fixNewUserlFiles() throws IOException{


    System.out.println("Creating all nessecary user files...");
    new File("server/directories/directory_" + GroupId + clientId).mkdirs();

    // add user to social graph txt
    socialLoader.addUser(clientId);
    
    try {

      File file = new File("server/directories/directory_" + GroupId + clientId + "/notifications.txt");
      file.createNewFile();
      System.out.println("File: " + file + " created.");

      File file2 = new File("server/profiles/Profile_" + GroupId + clientId + ".txt");
      file2.createNewFile();
      System.out.println("File: " + file2 + " created.");


      File file3 = new File("server/profiles/Others_"+ GroupId + clientId + ".txt");
      file3.createNewFile();
      System.out.println("File: " + file3 + " created.");

    }catch(Exception e) {
      e.printStackTrace();
    }


  }


  private void updateClientProfileFiles() throws IOException{
    String sourceFile = "server/profiles/Profile_" + GroupId + clientId + ".txt";
    String content = Files.readString(Paths.get(sourceFile));
    outStream.writeObject(content);
    System.out.println("Sending Profile_" + GroupId + clientId + ".txt");

    String sourceFile2 = "server/profiles/Others_" + GroupId + clientId + ".txt";
    String content2 = Files.readString(Paths.get(sourceFile2));
    outStream.writeObject(content2);
    System.out.println("Sending Others_" + GroupId + clientId + ".txt");
  }

  // --------------------------------------------------------------------------------------------------------------------------------------





}