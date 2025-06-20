package server;

import common.Packet;
import common.SenderState;

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

    //Volatile flag to stop GoBackN packets after download is completed
    private volatile boolean stopResending = false;


    // Constructor
    public ClientHandler(Socket socket) throws IOException {
    this.clientSocket = socket;
    }

    public void run() {

        try {
            // Streams
            outStream = new ObjectOutputStream(clientSocket.getOutputStream());
            inStream = new ObjectInputStream(clientSocket.getInputStream());
            outStream.flush();

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
                //Check notifications file
                checkNotifications();

                // Read menu action
                String actionCode = (String) inStream.readObject();
                System.out.println("client.Client message: " + actionCode);

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

    // Login Menu Option 1
    // Login
    private void login() throws IOException, ClassNotFoundException {

    // Read username and password
    String userName = (String) inStream.readObject();
    String password = (String) inStream.readObject();

    // Search for client's ID and successfully login, from users.txt using username and password
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

      updateClientsDirectoryFiles();
      updateClientProfileFiles();

    } else {
      outStream.writeObject("FailedLogin");
      outStream.flush();

      // resetLogin to retry
      this.login();
    }
    }

    // Login Menu Option 2
    // Sign Up
    private void signup() throws IOException, ClassNotFoundException {
        // Read username and password
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();
        String language = (String) inStream.readObject();

        // Check if username already exists
        if (usersLoader.getUserInfo(userName).isEmpty()) {

          // Generate random client ID and check to be unique
          String tempId = Integer.toString((int) (Math.random() * 101));
          while (usersLoader.getUserName(tempId).isEmpty()) {
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

          fixNewUserFiles();

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
    // Upload
    private void handleUpload() throws IOException, ClassNotFoundException {

        // Check handshake
        if (uploadHandshake()) {

            System.out.println("Upload sequence initiated");
            Map<Integer, byte[]> receivedPackets = new TreeMap<>();

            String imgNameGiven = (String) inStream.readObject();
            System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(filename)");
            String[] imgNameArray = imgNameGiven.split("\\.");


            // send client preferred language
            String languageShort = usersLoader.getUsersLanguage(clientId);
            outStream.writeObject(languageShort);
            String languagePref;
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

            byte[] imageBytes = new byte[fullData.length - 1 - descLength];
            System.arraycopy(fullData, 1 + descLength, imageBytes, 0, imageBytes.length);

            // Save image
            FileOutputStream fos = new FileOutputStream("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameGiven);

            // Create txt with description given
            File file = new File("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameArray[0] + ".txt");
            if ( !file.createNewFile() ) {
                System.out.println("File already exists.");
            }

            FileWriter fw = new FileWriter(file);
            fw.write(languagePref + " " +  description1 + " " + imgNameGiven);
            if(!description2.isEmpty()){
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
            System.out.println("Handshake Failed! Try again :(");
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
    // Search
    private void handleSearch() throws IOException, ClassNotFoundException, InterruptedException {

        // read input image name from client
        String searchImgName = (String) inStream.readObject();

        // read input language from client
        String searchImgLang = (String) inStream.readObject();

        ArrayList<String> following = socialLoader.getFollowing(clientId);

        ArrayList<String[]> results = new ArrayList<>();
        boolean foundExactMatch = false;

        String profileTxtpath;
        for (String tempclientId : following) {
            profileTxtpath = "server/profiles/Profile_" + GroupId + tempclientId + ".txt";

            try {

                lockFile(profileTxtpath);

                BufferedReader reader = new BufferedReader(new FileReader(profileTxtpath));
                String line;

                while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {

                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String photoFullName = line.split("\\s+")[2];
                        if (photoFullName.trim().toLowerCase().contains(searchImgName.trim().toLowerCase())) {
                            // filter image found by language
                            String txtPath = "server/directories/directory_" + GroupId + tempclientId + "/" + photoFullName.split("\\.")[0] + ".txt";
                            lockFile(txtPath);
                            BufferedReader reader2 = new BufferedReader(new FileReader(txtPath));
                            String txtLine;
                            boolean foundTxtWithCorrectLanguage = false;
                            while ((txtLine = reader2.readLine()) != null) {
                                if(txtLine.contains(searchImgLang)) {
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

    private void handleDownload(ArrayList<String[]> imageInfo) throws ClassNotFoundException, IOException, InterruptedException {

      if (downloadHandshake()) {
          System.out.println("Download sequence initiated");

          // read user selection from client
          int userSelectionNum = (int) inStream.readObject();
          System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(user selection)");

          String imageName = imageInfo.get(userSelectionNum)[2];
          String descriptionName = imageName.split("\\.")[0] + ".txt";

          String permissionResponse = askPermission(imageName, imageInfo.get(userSelectionNum)[0]);
          outStream.writeObject(permissionResponse);
          if(permissionResponse.equals("Rejected")){
              System.out.println("Access to the file was denied by uploader");
              return;
          }

          downloadSomething(imageName, descriptionName, imageInfo.get(userSelectionNum)[0]);

          lockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");
          // update profile.txt
          FileWriter proFileServerWriter = new FileWriter("server/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
          proFileServerWriter.append("\n");
          proFileServerWriter.append(clientId).append(" reposted ").append(imageName);
          proFileServerWriter.close();
          unlockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");

          lockFile("client/profiles/" + "Profile_" + GroupId + clientId + ".txt");
          // update profile.txt
          FileWriter proFileServerWriter2 = new FileWriter("client/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
          proFileServerWriter2.append("\n");
          proFileServerWriter2.append(clientId).append(" reposted ").append(imageName);
          proFileServerWriter2.close();
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
        System.out.println("\nDownload Handshake Failed! Try again :(");
      }
    }

    private String askPermission(String imageName, String IdToDownloadFrom) throws InterruptedException, IOException {

        String pathToWrite = "server/directories/directory_" + GroupId + IdToDownloadFrom + "/notifications.txt";
        lockFile(pathToWrite);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
            writer.write("download " + clientId + " " + imageName);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to " + pathToWrite);
        }
        unlockFile(pathToWrite);

        boolean hasResponseArrived = false;
        boolean otherClientAccepted = false;
        String myNotificationsPath = "server/directories/directory_" + GroupId + clientId + "/notifications.txt";
        while(!hasResponseArrived){
            System.out.println("Waiting for response...");
            Thread.sleep(5000);

            lockFile(myNotificationsPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(myNotificationsPath))) {

                String line;
                String notificationType;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    String[] splitNotification = line.split("\\s+");
                    notificationType = splitNotification[0];

                    if ( !(splitNotification.length == 3) ) continue;

                    if (notificationType.equals("RejectedDownload") && splitNotification[2].equals(imageName)) {
                        hasResponseArrived = true;
                        break;
                    } else if (notificationType.equals("AcceptedDownload") && splitNotification[2].equals(imageName)){
                        hasResponseArrived = true;
                        otherClientAccepted = true;
                        break;
                    }
                }

            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            unlockFile(myNotificationsPath);
        }

        deleteDownloadNotification();
        return otherClientAccepted? "Accepted" : "Rejected";
    }

    private void deleteDownloadNotification() throws IOException {
        Path path = Paths.get("server/directories/directory_" + GroupId + clientId + "/notifications.txt");
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean notificationFound = false;

        for (String line : lines) {

            String[] parts = line.trim().split("\\s+");

            if ( parts.length > 0 && ( parts[0].equals("RejectedDownload") || parts[0].equals("AcceptedDownload") )) {
                notificationFound = true;
                continue;
            }

            updatedLines.add(line);
        }

        if(notificationFound) {
            // Rewrite the file
            Files.write(path, updatedLines);
            System.out.println("Deleted notification.");
        }
    }

    // Handshake for download
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


    // Menu Option 3
    // Follow
    private void handleFollow() throws IOException, ClassNotFoundException {
      String response;
      // read input from client
      String userToFollow = (String) inStream.readObject();
      String userToFollowId = usersLoader.getUserId(userToFollow);
      if (userToFollowId.isEmpty()){
          response = "User not found! Try again.";
      }else{
          ArrayList<String> userIdStructure = new ArrayList<>();
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
              writer.write("follow " + clientId);
              writer.newLine();
          } catch (IOException e) {
              System.err.println("Failed to write to " + filePath);
          }
      }
    }


    // Menu option 4
    private void handleUnfollow() throws ClassNotFoundException, IOException{
      String response;

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

        List<String> usersList = new ArrayList<>(usersLoader.getAllUsers());

        // Send to client a list of all usernames on users.txt
        outStream.writeObject(usersList);
        outStream.flush();

        while (flag) {

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

                lockFile("server/profiles/Profile_" + GroupId + selectedID + ".txt");
                BufferedReader reader = new BufferedReader(new FileReader("server/profiles/Profile_" + GroupId + selectedID + ".txt"));
                String line;

                // Keep all available images to download from profile
                ArrayList<String[]> imagesToDownload = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    ArrayList<String> splitLine = new ArrayList<>(Arrays.asList(line.split("\\s+")));

                    if (splitLine.size() == 3) {
                        if (splitLine.get(1).equals("posted") || splitLine.get(1).equals("reposted")) {
                            imagesToDownload.add(new String[]{selectedID, profileName, splitLine.get(2)});
                        }
                    }
                }

                unlockFile("server/profiles/Profile_" + GroupId + selectedID + ".txt");

                // Send img list to client
                outStream.writeObject(imagesToDownload);
                outStream.flush();

                // Read which action user wants to perform [download/comment]
                String selectedAction = (String)inStream.readObject();

                if(selectedAction.equals("download")){
                    handleDownload(imagesToDownload);
                } else {
                    handleComment(selectedID);
                }


            } else {
                outStream.writeObject("access_denied");
                outStream.flush();
            }

            if (((String) inStream.readObject()).equals("no_retry")) flag = false;
        }
    }

    private void handleComment(String uploaderId) throws IOException, ClassNotFoundException, InterruptedException {
        System.out.println("\nComment Function Initiated.\n");

        String imageName = (String)inStream.readObject();

        // read from client as "[comment1,comment2,comment3]" split byt space string
        String comment = (String)inStream.readObject();

        String permissionResponse = askCommentPermission(imageName, comment, uploaderId);
        outStream.writeObject(permissionResponse);

        if(permissionResponse.equals("Rejected")){
          System.out.println("\nComment was denied by uploader!\n");
          return;
        }
        System.out.println("\nAccess for comment granted by uploader!");

        String commentLine = clientId + " commented " + comment + " " + imageName + " " + uploaderId;

        // update server profile.txt
        lockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");
        FileWriter proFileServerWriter = new FileWriter("server/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(commentLine);
        proFileServerWriter.close();
        unlockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");

        // update client profile.txt
        lockFile("client/profiles/" + "Profile_" + GroupId + clientId + ".txt");
        FileWriter proFileServerWriter2 = new FileWriter("client/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
        proFileServerWriter2.append("\n");
        proFileServerWriter2.append(commentLine);
        proFileServerWriter2.close();
        unlockFile("server/profiles/" + "Profile_" + GroupId + clientId + ".txt");

        System.out.println("\nComment was successfully added!\n");
    }

    private String askCommentPermission(String imageName,String comment, String IdToDownloadFrom) throws InterruptedException, IOException {

        String pathToWrite = "server/directories/directory_" + GroupId + IdToDownloadFrom + "/notifications.txt";
        lockFile(pathToWrite);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
            writer.write("comment " + clientId + " " + imageName + " " + comment);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write to " + pathToWrite);
        }
        unlockFile(pathToWrite);

        boolean hasResponseArrived = false;
        boolean otherClientAccepted = false;
        String myNotificationsPath = "server/directories/directory_" + GroupId + clientId + "/notifications.txt";
        while(!hasResponseArrived){
            System.out.println("Waiting for response...");
            Thread.sleep(5000);

            lockFile(myNotificationsPath);
            try (BufferedReader reader = new BufferedReader(new FileReader(myNotificationsPath))) {

                String line;
                String notificationType;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    System.out.println("Line: " + line);

                    String[] splitNotification = line.split("\\s+");
                    notificationType = splitNotification[0];
                    System.out.println("33Notification: " + splitNotification[0] + " " + splitNotification[1]);
                    if ((splitNotification.length != 4) ) continue;

                    System.out.println("36Notification: " + splitNotification[0] + " " + splitNotification[1]);

                    if (notificationType.equals("RejectedComment") && splitNotification[1].equals(imageName)) {
                        hasResponseArrived = true;
                        break;
                    } else if (notificationType.equals("AcceptedComment") && splitNotification[1].equals(imageName)){
                        hasResponseArrived = true;
                        otherClientAccepted = true;
                        break;
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            unlockFile(myNotificationsPath);
        }
        System.out.println("Response received: " + otherClientAccepted);
        deleteCommentNotification();
        return otherClientAccepted? "Accepted" : "Rejected";
    }

    private void deleteCommentNotification() throws IOException {
        Path path = Paths.get("server/directories/directory_" + GroupId + clientId + "/notifications.txt");
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean notificationFound = false;

        for (String line : lines) {

            String[] parts = line.trim().split("\\s+");

            if ( parts.length > 0 && ( parts[0].equals("RejectedComment") || parts[0].equals("AcceptedComment") )) {
                notificationFound = true;
                continue;
            }

            updatedLines.add(line);
        }

        if(notificationFound) {
            // Rewrite the file
            Files.write(path, updatedLines);
            System.out.println("Deleted notification.");

        }

    }

    private void sendCommentResponse(ArrayList<String[]> commentResponses) {
        String pathToWrite;
        for (String[] responseInfo : commentResponses){
            pathToWrite = "server/directories/directory_" + GroupId + responseInfo[0] + "/notifications.txt";
            lockFile(pathToWrite);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
                writer.write(responseInfo[1]);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write to " + pathToWrite);
            }
            unlockFile(pathToWrite);
        }

    }

    private void sendDownloadResponse(ArrayList<String[]> downloadResponses) {

        String pathToWrite;
        for (String[] responseInfo : downloadResponses){
            pathToWrite = "server/directories/directory_" + GroupId + responseInfo[0] + "/notifications.txt";
            lockFile(pathToWrite);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(pathToWrite, true))) {
                writer.write(responseInfo[1]);
                writer.newLine();
            } catch (IOException e) {
                System.err.println("Failed to write to " + pathToWrite);
            }
            unlockFile(pathToWrite);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------


    // GENERAL FUNCTIONS -------------------------------------------------------------------------------------------------------------------

    // Sync all files from server to client
    private void updateClientsDirectoryFiles() throws IOException {

        System.out.println("Synchronizing files with client...");

        File directoryFolder = new File("server/directories/directory_" + GroupId + clientId);
        File[] files = directoryFolder.listFiles();

        boolean imgTag;
        String fileName;
        String descriptionName;

        if (files != null) {

          for (File file : files) {
            fileName = file.getName();
            imgTag = fileName.contains(".jpg") || fileName.contains(".png") || fileName.contains(".jpeg") || fileName.contains(".JPG") || fileName.contains(".PNG") || fileName.contains(".JPEG");

            if (imgTag) {

                outStream.writeObject(fileName);
                descriptionName = fileName.split("\\.")[0] + ".txt";
                downloadSomething(fileName, descriptionName, clientId);

            }
          }

          // Send DONE
          outStream.writeObject("DONE");

        } else {
          outStream.writeObject("NotFound");
        }

    }

    // Sync profile file from server to client
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

        ArrayList<String> followNotifications = new ArrayList<>();
        ArrayList<String> downloadNotifications = new ArrayList<>();
        ArrayList<String[]> commentNotifications = new ArrayList<>();
        String notificationsPath = "server/directories/directory_" + GroupId + clientId + "/notifications.txt";

        lockFile(notificationsPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(notificationsPath))) {

          String line;
            String notificationType;
            String notificationSenderId;
            String notificationPhotoName;
            String comment;
          while ((line = reader.readLine()) != null) {
            if ( line.trim().isEmpty() ) continue;

            notificationType = line.split("\\s+")[0];
            notificationSenderId = line.split("\\s+")[1];

              switch (notificationType) {
                  case "follow" -> followNotifications.add(notificationSenderId);
                  case "download" -> {
                      notificationPhotoName = line.split("\\s+")[2];
                      downloadNotifications.add(notificationSenderId + " " + notificationPhotoName);
                  }
                  case "comment" -> {
                      notificationPhotoName = line.split("\\s+")[2];
                      comment = line.split("\\s+")[3];
                      if (Math.random() < 0.5) {
                          System.out.println("Comment Rejected.");
                          commentNotifications.add(new String[]{notificationSenderId, "RejectedComment " + notificationPhotoName + " " + clientId + " " + comment});
                      } else {
                          System.out.println("Comment Accepted.");
                          commentNotifications.add(new String[]{notificationSenderId, "AcceptedComment " + notificationPhotoName + " " + clientId + " " + comment});
                      }
                  }
              }
          }

        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        unlockFile(notificationsPath);

        // send notifications to client
        outStream.writeObject(followNotifications);
        outStream.writeObject(downloadNotifications);
        outStream.writeObject(commentNotifications);

        ArrayList<String> responses = new ArrayList<>();

        if (!followNotifications.isEmpty()) {

            for (String notification : followNotifications) {
                responses.add((String) inStream.readObject());
            }

            String[] splitResponse;
            ArrayList<String> acceptFrom = new ArrayList<>();
            ArrayList<String> sendTo = new ArrayList<>();

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

            socialLoader.acceptFollowRequests(clientId, acceptFrom);
            sendFollowRequests(clientId, sendTo);
            responses.clear();

            // delete follow notifications
            deleteFollowNotification();
        }

        if (!downloadNotifications.isEmpty()) {

            for (String notification : downloadNotifications) {
                responses.add((String) inStream.readObject());
            }

            String[] splitDownloadResponse;
            ArrayList<String[]> downloadResponses = new ArrayList<>();

            for (String response : responses) {

                splitDownloadResponse = response.split("\\s+");
                switch (splitDownloadResponse[0]) {

                    case "1":
                        System.out.println("Client " + clientId + " accepted download request from " + splitDownloadResponse[1] +
                                "for the photo" + splitDownloadResponse[2]);
                        downloadResponses.add(new String[]{
                                splitDownloadResponse[1],
                                "AcceptedDownload " + clientId + " " + splitDownloadResponse[2]
                        });
                        break;

                    case "2":
                        //reject
                        System.out.println("Client " + clientId + " rejected download request from " + splitDownloadResponse[1] +
                                "for the photo" + splitDownloadResponse[2]);
                        downloadResponses.add(new String[]{
                                splitDownloadResponse[1],
                                "RejectedDownload " + clientId + " " + splitDownloadResponse[2]
                        });
                        break;

                }
            }

            sendDownloadResponse(downloadResponses);
            responses.clear();

        }

        if (!commentNotifications.isEmpty()) {
          sendCommentResponse(commentNotifications);
        }
    }

    private void deleteFollowNotification() throws IOException {
        Path path = Paths.get("server/directories/directory_" + GroupId + clientId + "/notifications.txt");
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean notificationFound = false;

        for (String line : lines) {

            String[] parts = line.trim().split("\\s+");

            if ( parts.length > 0 && ( parts[0].equals("follow") )) {
                notificationFound = true;
                continue;
            }

            updatedLines.add(line);
        }

        if(notificationFound) {
            // Rewrite the file
            Files.write(path, updatedLines);
            System.out.println("Deleted follow notification.");
        }
    }    

    // General function to handle download from server to client
    private void downloadSomething(String imageName, String descriptionName, String userId) throws IOException {

        // Directories
        String imageDirectory = "server/directories/" + "directory_" + GroupId + userId + "/" + imageName;
        String descriptionDirectory = "server/directories/" + "directory_" + GroupId + userId + "/" + descriptionName;

        System.out.println("Image Directory: " + descriptionDirectory);

        Path imagePath = Paths.get(imageDirectory);

        StringBuilder descriptionLine = new StringBuilder();
        String txtLine = "";
        byte[] descriptionBytes;
        int descriptionLength;

        byte[] imageBytes = Files.readAllBytes(imagePath);

        lockFile(descriptionDirectory);

        // try catch to check if bind txt exists
        try {
            BufferedReader reader = new BufferedReader(new FileReader(descriptionDirectory));
            while ((txtLine = reader.readLine()) != null) {
                descriptionLine.append(txtLine).append("\n");
            }
            //   descriptionLine = reader.readLine();
            reader.close();
            outStream.writeObject("Selected Picture has bind .txt file.");

        } catch (Exception e) {

          // 9.g
          outStream.writeObject("Selected Picture didn't have bind .txt file.");
        }

        unlockFile(descriptionDirectory);

        descriptionBytes = descriptionLine.toString().getBytes();
        descriptionLength = descriptionBytes.length;

        // Combine data
        byte[] fullData = new byte[1 + descriptionLength + imageBytes.length];
        fullData[0] = (byte) descriptionLength;
        System.arraycopy(descriptionBytes, 0, fullData, 1, descriptionLength);
        System.arraycopy(imageBytes, 0, fullData, 1 + descriptionLength, imageBytes.length);

        // Divide into 10 packets
        int packetSize = (int) Math.ceil(fullData.length / 10.0);
        boolean ignoreNext = false;

        stopResending = false;

        SenderState state = new SenderState(); // Class has the GBN window base & sequence number for the next packet to be sent

        int windowSize = 3;
        Timer timer = new Timer();
        Map<Integer, Packet> packetBuffer = new HashMap<>();
        Set<String> receivedAcks = new HashSet<>();

        while (state.base < 10) {
            while (state.nextSeqNum < state.base + windowSize && state.nextSeqNum < 10) {
                int start = state.nextSeqNum * packetSize;
                int end = Math.min(start + packetSize, fullData.length);
                byte[] chunk = new byte[end - start];

                System.arraycopy(fullData, start, chunk, 0, chunk.length);
                Packet packet = new Packet(state.nextSeqNum, chunk);

                System.out.println("Sending packet " + state.nextSeqNum);

                outStream.writeObject(packet);
                outStream.flush();

                packetBuffer.put(state.nextSeqNum, packet); // Link sequence number to packet

                if (state.base == state.nextSeqNum) {
                    // Start timer
                    startTimer(timer, () -> {
                        if (stopResending) return;  // Prevent resends after GBN ends
                        synchronized (state) {
                            if (state.base >= 10) return; // Make sure you don't resend anything after base becomes 10
                            resendPackets(state, packetBuffer, outStream);
                        }
                    });
                }

                state.nextSeqNum++;
            }

            int originalTimeout = clientSocket.getSoTimeout();
            try {
                clientSocket.setSoTimeout(3000);
                Object ackObj = inStream.readObject();

                if (ackObj instanceof String ack && ack.startsWith("ACK")) {
                    int ackNum = Integer.parseInt(ack.substring(3));
                    System.out.println("Received but not yet accepted: " + ack);

                    if (!receivedAcks.contains(ack)) {
                        receivedAcks.add(ack); // If Server has not received this ACK before add it to receivedAcks
                        System.out.println("Received and ACCEPTED: ACK" + ackNum);
                    }

                    if (ackNum == state.base) {
                        // Increase GBN window

                        do {
                            state.base++;
                        } while (receivedAcks.contains("ACK" + state.base));

                        // When base becomes 10, we have sent all packets from 0 to 9
                        if (state.base == 10) {
                            timer.cancel();
                            break;
                        } else {
                            timer.cancel();
                            startTimer(timer, () -> {
                                if (stopResending) return;  // Prevent resends after GBN ends
                                synchronized (state) {
                                    if (state.base >= 10) return; // Make sure you don't resend anything after base becomes 10
                                    resendPackets(state, packetBuffer, outStream);
                                }
                            });                      }
                    }
                }

            } catch (SocketTimeoutException | ClassNotFoundException e) {

                System.out.println("ACK timeout! Resending from packet " + state.base);

                // Retransmit from base
                for (int i = state.base; i < state.nextSeqNum; i++) {
                    Packet resendPacket = packetBuffer.get(i);
                    outStream.writeObject(resendPacket);
                    outStream.flush();
                    System.out.println("Resent packet " + i);
                }
            } finally {
                try {
                    stopResending = true;
                    timer.cancel();  // To catch timeout exits or exceptions
                    timer.purge();
                    clientSocket.setSoTimeout(originalTimeout); // Restore timeout
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("--------------------------------------------------Done-----------------------------------------------------");
        outStream.writeObject("Transmission Complete");
        outStream.flush();
    }

    // Starts timer for timeout
    private void startTimer(Timer timer, Runnable task) {
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, 3000);
    }

    // Resends all packets in GBN window
    private void resendPackets(SenderState state, Map<Integer, Packet> packetBuffer, ObjectOutputStream outStream) {
        if (state.base >= state.nextSeqNum ) {
            return; // Don't resend if transmission is already complete
        }

        try {
            System.out.println("Timeout! Resending packets from " + state.base + " to " + (state.nextSeqNum - 1));

            // Resending packets that are in GBN window
            for (int i = state.base; i < state.nextSeqNum; i++) {
                Packet resendPacket = packetBuffer.get(i);

                outStream.writeObject(resendPacket);
                outStream.flush();

                System.out.println("Resent packet " + i);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Locks selected file
    private void lockFile(String filePath) {

        fileLocks.putIfAbsent(filePath, new ReentrantLock());
        ReentrantLock lock = fileLocks.get(filePath);

        while (true) {

          if (lock.tryLock()) {
            System.out.println("\nClient " + clientId + ": granted access to " + filePath);
            break;

          } else {

            System.out.println("\nClient " + clientId + ": Trying to access file: " + filePath + " but is locked. Waiting...");

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
          System.out.println("File Unlocked: " + filePath + "\n");
        }

    }

    // Creates necessary files for client after signup
    private void fixNewUserFiles() throws IOException{

        System.out.println("Creating all necessary user files...");
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

        } catch(Exception e) {
          e.printStackTrace();
        }

    }

  // --------------------------------------------------------------------------------------------------------------------------------------

}