package client;

import common.Packet;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;

public class Client {

  // Streams
  private ObjectOutputStream out;
  private ObjectInputStream in;

  // Sockets, IP and Port
  private Socket connection;
  private final String serverIP;
  private final int serverPort;

  // Menu Flags
  private boolean loginFlag = false;
  private boolean menuFlag = false;

  // Scanner
  private final Scanner myObj = new Scanner(System.in);

  // Group Id
  private final String GroupId = "45";

  // Client Id
  private String clientId;

  // Constructor
  public Client(String ip, int port) {
    this.serverIP = ip;
    this.serverPort = port;
  }

  // Main 
  public static void main(String[] args) {
    Client client = new Client("localhost", 303);
    System.out.println("\nEstablishing... connection with server!\n");
    client.startConnection();
  }
  
  // Start connection with server and handle Login and Main Menus
  public void startConnection() {
    try {

      connection = new Socket(serverIP, serverPort);

      out = new ObjectOutputStream(connection.getOutputStream());
      in = new ObjectInputStream(connection.getInputStream());

      out.flush();

      System.out.println("Connection Established!");

      out.writeObject("Established connection with server!");
      out.flush();

      // Login Menu
      while(!loginFlag) {
        
        // Print Login Menu
        System.out.println("**** Login Menu ****");
        System.out.println("\n1) Log In\n2) Sign Up\n3) Exit\n\nPlease insert a valid action number from 1-3 to continue:");
        String actionCode = myObj.nextLine();

        // Check if the action code is valid
        while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3"))) {
          System.out.println("Wrong Input! Please insert a valid action number from 1-3:");
          actionCode = myObj.nextLine();                    
        }

        // Send client's action code to the server
        out.writeObject(actionCode);
        out.flush();

        // Switch action code
        switch (actionCode) {

          case "1":
            // Login
            login();
            break;

          case "2":
            // Sign Up
            signup();
            break;

          case "3":
            // Exit
            stopClient();
            break;

        }

      }

      // Main Menu
      while(!menuFlag) {
        // Check the notifications file
        checkNotifications();

        // Print Main Menu
        System.out.println("\n****Main Menu ****");
        System.out.println("\n1) Upload Image from Client to Server\n2) Search Image on server\n3) Follow\n4) Unfollow\n5) Access Profile\n6) Exit");
        System.out.println("\nPlease insert a valid action number from 1-6 to continue:");
        String actionCode = myObj.nextLine();

        // Check if the action code is valid
        while (!(Objects.equals(actionCode, "1") || Objects.equals(actionCode, "2") || Objects.equals(actionCode, "3") || Objects.equals(actionCode, "4") || Objects.equals(actionCode, "5") || Objects.equals(actionCode, "6"))) {
          System.out.println("Wrong Input!\nPlease insert a valid action number from 1-6 to continue:");
          actionCode = myObj.nextLine();
        }

        // Send client's action code to the server
        out.writeObject(actionCode);
        out.flush();

        // Switch action code
        switch (actionCode) {

          case "1":
            // Upload Image
            uploadImg();
            break;

          case "2":
            // Search Image
            searchImg();
            break;

          case "3":
            // Follow
            follow();
            break;

          case "4":
            // Unfollow
            unfollow();
            break;

          case "5":
            // Access Profile
            accessProfile();
            break;

          case "6":
            // Exit
            stopClient();
            break;
        }
      }

    } catch (IOException e) {
      System.out.println(e.getMessage());
    } catch (ClassNotFoundException | InterruptedException e) {
      throw new RuntimeException(e);
    }

  }



  // LOGIN MENU -------------------------------------------------------------------------------------------------------------

  // Login Menu Option 1
  // Login
  public void login() throws IOException, ClassNotFoundException, InterruptedException {

    // Ask for username
    System.out.println("\nUsername:");
    String userName = myObj.nextLine();

    // Ask for password
    System.out.println("\nPassword:");
    String password = myObj.nextLine();

    // Send username and password to the server
    out.writeObject(userName);
    out.flush();

    out.writeObject(password);
    out.flush();

    // Server response for login
    String response = (String) in.readObject();

    if(response.equals("SuccessLogin")){

      // update local clientId variable
      clientId = (String) in.readObject();

      System.out.println("\nSuccessful login!\n");
      System.out.println("Welcome client " + clientId + ".\n");

      updateLocalFiles();

      loginFlag = true;



    }else{

      // Reset login
      System.out.println("\nFailed login! Username or password is incorrect.\nPlease try again.\n");
      this.login();
    }

  }


  // Login Menu Option 2
  // Sign Up
  public void signup() throws IOException, ClassNotFoundException {

    // Ask for username
    System.out.println("\nCreate a username:");
    String userName = myObj.nextLine();

    // Ask for password
    System.out.println("\nCreate a password:");
    String password = myObj.nextLine();

    // Ask for language
    String language = "";
    System.out.println("\nPrefered language:\n1) Greek\n2) English\nChoose a language by typing 1 or 2:");
    while (true) {
      String languageCode = myObj.nextLine();
      if (Objects.equals(languageCode, "1")) {
        language = "gr";
        break;
      } else if (Objects.equals(languageCode, "2")) {
        language = "en";
        break;
      }
    }

    // Send username and password language to the server
    out.writeObject(userName);
    out.flush();

    out.writeObject(password);
    out.flush();

    out.writeObject(language);
    out.flush();

    // Server response for sign up
    String response = (String) in.readObject();
    if(response.equals("SuccessSignUp")){

      clientId = (String) in.readObject();
      System.out.println("\nSuccessful sign up!\nYou are now logged in.\nWelcome client " + clientId + ".\n");
      loginFlag = true;

      fixNewUserlFiles();

    }else{
      System.out.println("\nFailed to sign up! Username already exists.\nPlease try different username.\n");
      this.signup();
    }
  }

  // ------------------------------------------------------------------------------------------------------------------------


  // MAIN MENU --------------------------------------------------------------------------------------------------------------

  // Main Menu Option 1
  // Upload Image
  private void uploadImg() throws IOException, ClassNotFoundException {

    // Check if handshake is accepted
    if(uploadHandshake()) {

      String pathname = "";
      String pathNameClean = "";

      // Check input filename
      while(true){

        // Ask for filename
        System.out.println("\nEnter filename please:");
        pathname = myObj.nextLine();

        boolean imgTag = pathname.contains(".jpg") || pathname.contains(".png") || pathname.contains(".jpeg") || pathname.contains(".JPG") || pathname.contains(".PNG") || pathname.contains(".JPEG");

        // Check if input inludes smt dot and is an image type AND if it is and image
        if((pathname.split("\\.").length == 2) && imgTag){
          break;
        }else{
          System.out.println("\nWrong input! Please insert a valid filename:");
        }

      }

      // Keeps only the first part of filename given  example.png ---> example
      pathNameClean = pathname.split("\\.")[0];

      // Send server img name
      out.writeObject(pathname);
      System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(filename)");

      // Receive from server users prefered language
      String languageShort = (String) in.readObject();
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

      // Load image and description
      String fullpathname = "client/directory/" + pathname;
      File imageFile = new File(fullpathname);

      byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

      // Ask for description in users prefered language
      System.out.println("\nEnter a description please in your preffered language (" + languagePref + ")" + " :");
      String description1 = myObj.nextLine();

      // Ask for description in users other language
      System.out.println("\nEnter a description please in other language (" + secondLanguagePref + ")" +  " :");
      String description2 = myObj.nextLine();

      out.writeObject(description1);
      out.writeObject(description2);

      // Create a txt with description given
      File file = new File("client/directory/" + pathNameClean + ".txt");

      // Check if file already exists
      if (!file.createNewFile()){
        System.out.println("\nFile already exists.");
      }

      // Write descriptions fot both languages if exists to file
      FileWriter fw = new FileWriter(file);
      fw.write(languagePref + " " +  description1 + " " + pathname);
      if(description2.length() > 0){
        fw.write("\n" + secondLanguagePref + " " +  description2 + " " + pathname);
      }
      fw.close();

      byte[] descriptionBytes = (description1 + description2).getBytes();
      int descLength = descriptionBytes.length;

      // Check if description is too long
      if (descLength > 255) {
        throw new IllegalArgumentException("Description too long for 1-byte length field");
      }


      // Combine data
      byte[] fullData = new byte[1 + descLength + imageBytes.length];
      fullData[0] = (byte) descLength;

      // Copy data
      System.arraycopy(descriptionBytes, 0, fullData, 1, descLength);
      System.arraycopy(imageBytes, 0, fullData, 1 + descLength, imageBytes.length);

      // Divide into 10 packets
      int packetSize = (int) Math.ceil(fullData.length / 10.0);
      for (int i = 0; i < 10; i++) {
        int start = i * packetSize;
        int end = Math.min(start + packetSize, fullData.length);

        byte[] chunk = new byte[end - start];

        System.arraycopy(fullData, start, chunk, 0, end - start);

        // Send packet to server
        Packet packet = new Packet(i, chunk);
        out.writeObject(packet);
        out.flush();

        // Wait ACK from server
        String ack = in.readLine();
        if (!ack.equals("ACK" + i)) {

          System.out.println("ACK mismatch or timeout. Resending...");
          i--; // resend

        } else {
          System.out.println("Received: " + ack);
        }
      }

      // update user profile.txt
      FileWriter proFileServerWriter = new FileWriter("client/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
      proFileServerWriter.append("\n");
      proFileServerWriter.append(clientId).append(" posted ").append(pathname);
      proFileServerWriter.close();

    }else{
      System.out.println("\nHandshake Failed! Try again :(");
    }
  }

  // Handshake function for upload
  private boolean uploadHandshake() throws IOException, ClassNotFoundException {

    // Send request
    out.writeObject("Request to upload");
    System.out.println("HANDSHAKE STEP 1: Client sent request");

    // Wait for response
    String handshakeResponse = (String) in.readObject();
    System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");

    // Check if handshake is accepted
    return handshakeResponse.equals("acceptedUpload");
  }


  // Main Menu Option 2
  // Search Images
  public void searchImg() throws IOException, ClassNotFoundException, InterruptedException {

      String searchImgInput = "";
      String languageImgInput = "";

      // check input filename
      while(true){
          System.out.println("\nEnter file you want to search:");
          searchImgInput = myObj.nextLine();

          System.out.println("\nChoose language you want to search:\n1) Greek\n2) English\nChoose a language by typing 1 or 2:");
          languageImgInput = myObj.nextLine();

          if(searchImgInput != null && !searchImgInput.isEmpty() && languageImgInput != null && !languageImgInput.isEmpty() && (languageImgInput.equals("1") || languageImgInput.equals("2"))){
              break;
          }else{
              System.out.println("\nWrong input! Please insert a valid filename and language to search.");
          }
      }

      String languageClear = "";
      switch (languageImgInput) {
        case "1":
          languageClear = "Greek";
          break;

        case "2":
          languageClear = "English";
          break;

        default:
          languageClear = "";
          break;
      }

      // send client's keyword and language  to the server
      out.writeObject(searchImgInput);
      out.flush();
      out.writeObject(languageClear);
      out.flush();

      // Server response
      @SuppressWarnings("unchecked")
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

        int userSelectionNum = Integer.parseInt(userSelection) - 1;

        // begin download process
        downloadImg(userSelectionNum, results.get(userSelectionNum));

      }else{
        System.out.println("No results found :(");
      }
  }

  // Download Picture
  private void downloadImg(int userSelectionNum, String[] imageInfo) throws ClassNotFoundException, IOException, InterruptedException {

    // Check if download is accepted by server
    if(downloadHandshake()) {

      // Send server user picture selection
      out.writeObject(userSelectionNum);
      System.out.println("HANDSHAKE STEP 3: Client sent sync acknowledgement(user selection)");

      String responseFromOtherClient = (String)in.readObject();
      if(responseFromOtherClient.equals("Rejected")){
        System.out.println("Access to the file was denied by uploader");
        return;
      }

      // Download picture
      downloadSomething(imageInfo[2], false);
    }
  }

  // Handshake function for download
  private boolean downloadHandshake() throws IOException, ClassNotFoundException {
    out.writeObject("request to download");
    System.out.println("HANDSHAKE STEP 1: Client sent request");
    String handshakeResponse = (String) in.readObject();
    System.out.println("HANDSHAKE STEP 2: Server sent acknowledgement");
    return handshakeResponse.equals("acceptedDownload");
  }


  // Main Menu Option 3
  // Follow other user function
  private void follow() throws IOException, ClassNotFoundException {

    String userToFollow = "";

    // Check input filename
    while(true){

      System.out.println("\nEnter the username of a user you want to follow");
      userToFollow = myObj.nextLine();

      if(userToFollow != null && !userToFollow.isEmpty()){
        break;
      }else{
        System.out.println("\nWrong input! Please insert a valid username:");
      }

    }

    // Send client's input to the server
    out.writeObject(userToFollow);
    out.flush();

    // Server response
    System.out.println((String)in.readObject());
  }


  // Main Menu Option 4
  // Unfollow other user
  private void unfollow() throws IOException, ClassNotFoundException {
      String userToUnFollow = "";

      // check input filename
      while(true){
          System.out.println("\nEnter the username of a user you want to unfollow.");
          userToUnFollow = myObj.nextLine();

          if(userToUnFollow != null && !userToUnFollow.isEmpty()){
              break;
          }else{
              System.out.println("\nWrong input! Please insert a valid username:");
          }
      }

      // send client's unfollow input to the server
      out.writeObject(userToUnFollow);
      out.flush();

      System.out.println("\n" + (String)in.readObject());
  }


  // Main Menu Option 5
  // Access Profile
  private void accessProfile() throws IOException, ClassNotFoundException, InterruptedException {
    boolean accessProfileFlag = true;
    ArrayList<String> usersList = (ArrayList<String>) in.readObject();

    while(accessProfileFlag) {
      System.out.println("\nWhich profile would you like to access? Insert a name from the list:");
      for (int i = 0; i < usersList.size(); i++) System.out.println((i + 1) + ". " + usersList.toArray()[i]);

      //Check for valid choice name from the userlist
      String choice = "";
      while (true) {

        choice = myObj.nextLine().trim(); // trim to avoid whitespace issues

        if (usersList.contains(choice)) {
          break;
        } else {
          System.out.println("\nWrong input! Please insert a name from the list.");
        }
      }

      // Send to server the profile name client wants to access
      out.writeObject(choice);
      out.flush();

      // Read server message (access_approved/denied)
      String response = (String) in.readObject();

      if (response.equals("access_approved")) {
        // Read profile
        String profileContent = (String) in.readObject();

        // Print profile content
        System.out.println("\n" + profileContent +"\n");



        ArrayList<String[]> imgsToDownload = (ArrayList<String[]>) in.readObject();

        System.out.println("The following images are available for download and/or comment:");
        for (int i=0; i<imgsToDownload.size(); i++) {
          System.out.println(i+1 + ". " + imgsToDownload.get(i)[2]);
        }

        String[] splitChoice;
        int imageNumber;
        String action;
        do {
          System.out.println("Start your input with the image number [1-" + imgsToDownload.size() + "]. After that write the action [download/comment].");

          choice = myObj.nextLine(); // Read user input

          splitChoice = choice.split("\\s+"); // Split the user input into parts
          // Check if we have enough parts. If not, assign dummy values to the variables
          if (splitChoice.length != 2) {
            imageNumber = 0;
            action = "";
            continue;
          }
          //Extract user input
          imageNumber = splitChoice[0].matches("\\d+") ? Integer.parseInt(splitChoice[0]) : 0;
          action = splitChoice[1];

          // Check whether user input is valid
        } while ( ! ( (imageNumber >= 1 && imageNumber <= imgsToDownload.size())
                && ( action.equals("download") || action.equals("comment") ) )
        );


        out.writeObject(action);
        if ( action.equals("download") ){
          downloadImg(imageNumber-1, imgsToDownload.get(imageNumber-1));
        } else{
          handleComment(imageNumber-1, imgsToDownload.get(imageNumber-1));
        }


      } else {
        System.out.println("Access denied! You can only access profiles of the users that you follow.\n");
      }

      do {
        System.out.println("\nWould you like to access another profile? [y/n]");
        choice = myObj.nextLine();

      } while (!(choice.equals("y") | choice.equals("Y") | choice.equals("n") | choice.equals("N")));

      if (choice.equals("n") | choice.equals("N")) {
        accessProfileFlag = false;

        out.writeObject("no_retry");
        out.flush();

      } else {
        out.writeObject("retry");
        out.flush();
      }
    }
  }

  private void handleComment(int userSelectionNum, String[] imageInfo) throws IOException, ClassNotFoundException {

    out.writeObject(imageInfo[2]);

    System.out.println("\nWrite a comment for image:" + imageInfo[2]);
    String comment = myObj.nextLine();

    String fixedComment = Arrays.toString(comment.split("\\s+")).replace(" ", "");

    out.writeObject(fixedComment);
    out.flush();

    String responseFromOtherClient = (String)in.readObject();
    if(responseFromOtherClient.equals("Rejected")){
      System.out.println("Comment was denied by uploader!\n");
      return;
    }
    System.out.println("Access for comment granted by uploader!\n");


  }

  // -----------------------------------------------------------------------------------------------------------------------


  // GENERAL FUNCTIONS ----------------------------------------------------------------------------------------------------------------------------------------

  // Checks user notifications txt file for new notifications after login
  private void checkNotifications() throws IOException, ClassNotFoundException {


    // Notification Format: "sender's clientId"
    ArrayList<String> followNotifications = (ArrayList<String>)in.readObject();

    // Notification Format: "sender's clientId [space] PhotoName"
    ArrayList<String> downloadNotifications = (ArrayList<String>)in.readObject();
    ArrayList<String> commentNotifications = (ArrayList<String>)in.readObject();

    int notificationCount = followNotifications.size() + downloadNotifications.size() + commentNotifications.size();
    String action = "";
    if(notificationCount == 0){
      System.out.println("You have already checked all your notifications! :)");
    }else{

      System.out.println("You have " + notificationCount + " notifications.\n");

      //Check follow notification and respond
      for (String notification : followNotifications) {

        System.out.println("User " + notification + " wants to follow you.");
        System.out.println("Would you like to: \n1) Accept, \n2) Reject,\n3) Accept and follow back");
        action = myObj.nextLine();
        while (!(Objects.equals(action, "1") || Objects.equals(action, "2") || Objects.equals(action, "3"))){
          System.out.println("Wrong Input!\nPlease insert a valid action number from 1-3 to continue:");
          action = myObj.nextLine();
        }

        switch (action) {

          case "1":
            System.out.println("Follow request accepted.");
            break;

          case "2":
            System.out.println("Follow request rejected.");
            break;

          case "3":
            System.out.println("Follow request accepted and followed back.");
            break;

        }

        //Output Format: " "action" " " "sender's clientId" "
        out.writeObject(action + " " + notification);
      }

      //Check download notifications and respond
      for (String notification : downloadNotifications){
        String[] notificationInfo = notification.split("\\s+");

        System.out.println("User " + notificationInfo[0] + " wants to download this photo: " + notificationInfo[1] );
        System.out.println("Would you like to: \n1) Accept, \n2) Reject");
        action = myObj.nextLine();
        while (!(Objects.equals(action, "1") || Objects.equals(action, "2") )){
          System.out.println("Wrong Input!\nPlease insert a valid action number from 1-2 to continue:");
          action = myObj.nextLine();
        }

        switch (action) {

          case "1":
            System.out.println(notificationInfo[1] + "download request accepted.");
            break;

          case "2":
            System.out.println(notificationInfo[1] + "download request rejected.");
            break;

        }

        //Output Format: " "action" " " "sender's clientId" " " "photoName" "
        out.writeObject(action + " " + notification);
      }

      System.out.println("\nNo more notifications to check.\n");

    }
  }

  // Empty client directories after login for a new session
  public static void emptyFolder(File folder) {

    // Get all the files from the folder
    File[] files = folder.listFiles();

    // Check if it is file and if it is delete it
    if(files!=null) {

      for(File f: files) {

        if(f.isDirectory()) {

          emptyFolder(f);

        }else{

          if (!f.delete()){
            System.out.println("Failed to delete file.");
          }

        }

      }

    }

  }

  // Updates client's local files after login
  private void updateLocalFiles() throws IOException, ClassNotFoundException, InterruptedException {

    // Empty client directories
    emptyFolder(new File("client/directory"));
    emptyFolder(new File("client/profiles") );

    String fileName = "";
    boolean finishedDownload = false;

    while(!finishedDownload){

      fileName = (String)in.readObject();

      if(fileName.equals("NotFound") || fileName.equals("DONE")){
        finishedDownload = true;

      }else{
        downloadSomething(fileName, true);
      }
    }

    copyProfileFromServer();

  }

  // General function to download a file from server to client images and bind txt files
  private void downloadSomething(String imgName, Boolean isLoginOrSignup) throws IOException, ClassNotFoundException, InterruptedException {

      // Array to keep track of the Acknowledgements not sent
      // The value firstTimeReceivingPackage[0] tells me whether I already received the 3rd package and so on
      boolean[] firstTimeReceivingPackage = {true,true, false,true,true,false,false,false,false,false};

      // 9.g message from server
      System.out.println("Txt status from server:\n" + in.readObject()+ "\n");

      int expectedSeqNum = 0;
      Map<Integer, byte[]> receivedPackets = new TreeMap<>();

      int delayedAcknowledgementsCounter = 0;

      while (receivedPackets.size() < 10) {
        Packet packet = (Packet) in.readObject();
        System.out.println("--------------------------------------");
        System.out.println("Received packet #" + packet.sequenceNumber);

        if (packet.sequenceNumber == expectedSeqNum) {

          if((expectedSeqNum == 2 || ( (expectedSeqNum >= 5) && (expectedSeqNum < 10 ) ) )
                  && delayedAcknowledgementsCounter <= 5
                  && !firstTimeReceivingPackage[expectedSeqNum]
                  && !isLoginOrSignup){

            System.out.println("--------------------------------------");
            System.out.println("Didn't send acknowledgement on purpose");
            firstTimeReceivingPackage[expectedSeqNum] = true;
            delayedAcknowledgementsCounter++;
            continue;
          }

          System.out.println("Sending Ack "+ expectedSeqNum + "");
          receivedPackets.put(packet.sequenceNumber, packet.data);

          out.writeObject("ACK" + packet.sequenceNumber);
          out.flush();

          expectedSeqNum++;

        } else {
          // Re-ack the last correct packet to help server
          out.writeObject("ACK" + (expectedSeqNum - 1));
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
      FileOutputStream fos = new FileOutputStream("client/directory/" + imgName);

      // Create txt with description given
      File file = new File("client/directory/" + imgName.split("\\.")[0] + ".txt");
      if ( !file.createNewFile() ) {
          System.out.println("\nFile already exists.");
      }
      FileWriter fw = new FileWriter(file);
      fw.write(description + " " + imgName);
      fw.close();

      // Update profile.txt
      if(!isLoginOrSignup){
        FileWriter proFileServerWriter = new FileWriter("client/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(clientId).append(" posted ").append(imgName);
        proFileServerWriter.close();
      }

      fos.write(imageBytes);
      fos.close();

    Object serverOutput = in.readObject();
    while (!(serverOutput instanceof String str)){
      System.out.println("Wrong Server Message, transmission still open. Message was "+ serverOutput);
      serverOutput = in.readObject();
    }

      if ( str.equals("Transmission Complete")) {
        System.out.println("\nThe transmission is completed!");
      } else {
        System.out.println("Wrong Server Message but it's a string " + str);
      }

  }

  // Option EXIT for both menus
  // Sign out Client and terminate everything necessary
  public void stopClient() {

    try {

      myObj.close();

      loginFlag = true;
      menuFlag = true;

      in.close();
      out.close();

      connection.close();

      System.out.println("Connection closed");

    } catch (IOException e) {
      System.out.println(e.getMessage());
    }

  }

  // Clean all client files for new user
  private void fixNewUserlFiles(){

    // Empty client directories
    emptyFolder(new File("client/directory"));
    emptyFolder(new File("client/profiles"));

    try {

      File file = new File("client/profiles/Profile_" + GroupId + clientId + ".txt");
      file.createNewFile();
      System.out.println("File: " + file + " created.");

      File file2 = new File("client/profiles/Others" + GroupId + clientId + ".txt");
      file2.createNewFile();
      System.out.println("File: " + file2 + " created.");

    }catch(Exception e) {
      e.printStackTrace();
    }
  }

  private void copyProfileFromServer() throws ClassNotFoundException, IOException{

    String content = (String) in.readObject();
    Files.writeString(Paths.get("client/profiles/Profile_" + GroupId + clientId + ".txt"), content);
    System.out.println("Received Profile_" + GroupId + clientId + ".txt");

    String content2 = (String) in.readObject();
    Files.writeString(Paths.get("client/profiles/Others_" + GroupId + clientId + ".txt"), content2);
    System.out.println("Received Others_" + GroupId + clientId + ".txt");

  }

  // ----------------------------------------------------------------------------------------------------------------------------------------------------------

}