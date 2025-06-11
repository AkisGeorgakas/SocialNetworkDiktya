package client;

import common.Packet;

import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

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

      checkNotifications();

    }else{

      // Reset login
      System.out.println("\nFailed login! Username or password is incorrect.\nPlease try again.\n");
      this.login();
    }

  }

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

      // Load image and description
      String fullpathname = "client/directory/" + pathname;
      File imageFile = new File(fullpathname);

      byte[] imageBytes = Files.readAllBytes(imageFile.toPath());

      // Ask for description
      System.out.println("\nEnter a description please:");
      String description = myObj.nextLine();

      // Create a txt with description given
      File file = new File("client/directory/" + pathNameClean + ".txt");

      // Check if file already exists
      if (!file.createNewFile()){
        System.out.println("File already exists.");
      }

      // Write description to file
      FileWriter fw = new FileWriter(file);
      fw.write(description + " " + pathname);
      fw.close();

      byte[] descriptionBytes = description.getBytes();
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

      // check input filename
      while(true){
          System.out.println("\nEnter file you want to search:");
          searchImgInput = myObj.nextLine();

          if(searchImgInput != null && !searchImgInput.isEmpty()){
              break;
          }else{
              System.out.println("\nWrong input! Please insert a valid filename:");
          }
      }

      // send client's keyword to the server
      out.writeObject(searchImgInput);
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
    boolean flag = true;
    ArrayList<String> usersList = (ArrayList<String>) in.readObject();

    while(flag == true) {
      System.out.println("Which profile would you like to access?");
      for (int i = 0; i < usersList.size(); i++) System.out.println((i + 1) + ". " + usersList.toArray()[i]);

      //TODO Check for valid choice
      String choice = myObj.nextLine();

      // Send to server the profile name client wants to access
      out.writeObject(choice);
      out.flush();

      // Read server message (access approved/denied)
      String response = (String) in.readObject();

      if (response.equals("access_approved")) {
        // Read profile
        String profileContent = (String) in.readObject();

        // Print profile content
        System.out.println("\n" + profileContent +"\n");

        ArrayList<String[]> imgsToDownload = (ArrayList<String[]>) in.readObject();

        System.out.println("The following images are available for download:");
        for (int i=0; i<imgsToDownload.size(); i++) {
          System.out.println(i+1 + ". " + imgsToDownload.get(i)[2]);
        }
        System.out.println("Choose an image to download [1-" + imgsToDownload.size() + "]");
        choice = myObj.nextLine();

        downloadImg(Integer.parseInt(choice)-1, imgsToDownload.get(Integer.parseInt(choice)-1));

      } else {
        System.out.println("Access denied! You can only access profiles of the users that you follow.\n");
      }

      do {
        System.out.println("\nWould you like to access another profile? [y/n]");
        choice = myObj.nextLine();

      } while (!(choice.equals("y") | choice.equals("Y") | choice.equals("n") | choice.equals("N")));

      if (choice.equals("n") | choice.equals("N")) {
        flag = false;

        out.writeObject("no_retry");
        out.flush();

      } else {
        out.writeObject("retry");
        out.flush();
      }
    }
  }

  // -----------------------------------------------------------------------------------------------------------------------





  // GENERAL FUNCTIONS ----------------------------------------------------------------------------------------------------------------------------------------

  // Checks user notifications txt file for new notifications after login
  private void checkNotifications() throws IOException, ClassNotFoundException {

    // Notification Format: "sender's clientId"
    @SuppressWarnings("unchecked")
    ArrayList<String> notifications = (ArrayList<String>)in.readObject();
    String action = "";
    if(notifications.isEmpty()){
      System.out.println("You have already checked all your notifications! :)");
    }else{

      System.out.println("You have "+notifications.size() + " notifications.\n");

      for (String notification : notifications) {

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

      Map<Integer, byte[]> receivedPackets = new TreeMap<>();
      ArrayList<Integer> receivedPacketseqNums = new ArrayList<>();
      // for the occasion of 9.e
      boolean firstTime3rdPackage = false;
      // for the occasion of 9.f
      boolean firstTime6thPackage = false;

      // 9.g message from server
      System.out.println("Txt status from server:\n" + in.readObject()+ "\n");

      // for loop to receive 10 packets
      for (int i = 0; i < 10; i++) {

          Packet packet = (Packet) in.readObject();
          System.out.println("Received packet #" + packet.sequenceNumber);
          if(!receivedPacketseqNums.contains(packet.sequenceNumber)){
              receivedPacketseqNums.add(packet.sequenceNumber);
              receivedPackets.put(packet.sequenceNumber, packet.data);
          }else{
              if(i!=9) {
                  System.out.println("Received a duplicate and didn't save it.");
                  i--;
              }
          }

          // for the occasion of 9.e
          if(i == 2 && !firstTime3rdPackage){
              System.out.println("Didn't send package on purpose");
              firstTime3rdPackage = true;
              // i--;
          }

          // for the occasion of 9.f
          else if (i == 5 && !firstTime6thPackage) {

              System.out.println("Delaying acknowledgement by 6 seconds...");
              TimeUnit.SECONDS.sleep(6);

              // Send ACK
              out.writeObject(("ACK" + packet.sequenceNumber));
              out.flush();

              firstTime6thPackage = true;
          }else{
              // Send ACK
              out.writeObject(("ACK" + packet.sequenceNumber));
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
          System.out.println("File already exists.");
      }
      FileWriter fw = new FileWriter(file);
      fw.write(description + " " + imgName);
      fw.close();

      // update profile.txt
      if(!isLoginOrSignup){
        FileWriter proFileServerWriter = new FileWriter("client/profiles/"+ "Profile_" + GroupId + clientId + ".txt"	,true);
        proFileServerWriter.append("\n");
        proFileServerWriter.append(clientId).append(" posted ").append(imgName);
        proFileServerWriter.close();
      }

      fos.write(imageBytes);
      fos.close();

      // 9.h)
      System.out.println("The transmission is completed!");
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