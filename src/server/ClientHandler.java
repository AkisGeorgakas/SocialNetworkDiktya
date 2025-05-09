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
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
                        loginFlag = true;
                        break;

                }

            }

            // menu
            while (!menuFlag) {
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

        if (uploadHandshake()) {
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
            FileOutputStream fos = new FileOutputStream("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameGiven);

            // Create txt with description given
            File file = new File("server/directories/" + "directory_" + GroupId + clientId + "/" + imgNameArray[0] + ".txt");
            file.createNewFile();
            FileWriter fw = new FileWriter(file);
            fw.write(description + " " + imgNameGiven);
            fw.close();

            // update profile.txt
            FileWriter proFileServerWriter = new FileWriter("server/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
            proFileServerWriter.append("\n");
            proFileServerWriter.append(clientId + " posted " + imgNameGiven);
            proFileServerWriter.close();


            fos.write(imageBytes);
            fos.close();


        } else {
            System.out.println("Hanshake Failed! Try again :(");
        }
    }


    // Menu Option 2
    private void handleSearch() throws IOException, ClassNotFoundException {

        // read input from client
        String searcImgName = (String) inStream.readObject();

        SocialGraphLoader socialLoader = new SocialGraphLoader();
        ArrayList<String> following = socialLoader.getFollowing(clientId);

        System.out.println("Following: " + following);

        ArrayList<String[]> results = new ArrayList<String[]>();
        boolean foundExactMatch = false;

        for (String clientId : following) {

            try {
                BufferedReader reader = new BufferedReader(new FileReader("server/profiles/Profile_" + GroupId + clientId + ".txt"));
                if (reader != null) {
                    String line;

                    while ((line = reader.readLine()) != null) {

                        String photoFullName = (line.split(" "))[2];
                        if (photoFullName.contains(searcImgName)) {

                            for (String[] result : results) {
                                if (result[2].equals(photoFullName)) {
                                    foundExactMatch = true;
                                    break;
                                }
                            }

                            if (!foundExactMatch) {
                                results.add(new String[]{clientId, usersLoader.getUserName(clientId), photoFullName});
                            }

                            foundExactMatch = false;
                        }
                    }
                    reader.close();
                }

            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
        outStream.writeObject(results);

        // int selectedImage =  Integer.parseInt((String)(inStream.readObject()));

        handleDownload(results);
    }

    private void handleDownload(ArrayList<String[]> imageInfo) throws ClassNotFoundException, IOException {
        if (downloadHandshake()) {
            System.out.println("Download sequence initiated");

            // read user selection from client
            int userSelectionNum = (int) inStream.readObject();

            String imageName = imageInfo.get(userSelectionNum)[2];
            String descriptionName = imageName.split("\\.")[0] + ".txt";
            downloadSomething(imageName, descriptionName, imageInfo.get(userSelectionNum)[0]);

            // update profile.txt
            FileWriter proFileServerWriter = new FileWriter("server/profiles/" + "Profile_" + GroupId + clientId + ".txt", true);
            proFileServerWriter.append("\n");
            proFileServerWriter.append(clientId + " reposted " + imageName);
            proFileServerWriter.close();

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
        if (handshakeResponse.equals("request to download")) {
            outStream.writeObject("acceptedDownload");
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

    private void login() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();
        clientId = usersLoader.checkUser(userName, password);

        if (clientId != null) {
            outStream.writeObject("SuccessLogin");
            outStream.flush();

            outStream.writeObject(clientId);
            outStream.flush();
            loginFlag = true;

            updateClientsLocalFiles();
            checkNotifications();
        } else {
            outStream.writeObject("FailedLogin");
            outStream.flush();

            // resetLogin
            this.login();
        }
    }

    private void signup() throws IOException, ClassNotFoundException {
        String userName = (String) inStream.readObject();
        String password = (String) inStream.readObject();

        if (usersLoader.getUserInfo(userName).isEmpty()) {
            clientId = Integer.toString((int) (Math.random() * 101));

            String formattedInfo = userName + ":" + password + "," + clientId;
            usersLoader.addUser(formattedInfo);

            outStream.writeObject("SuccessSignUp");
            outStream.flush();

            outStream.writeObject(clientId);
            outStream.flush();

            loginFlag = true;
        } else {
            outStream.writeObject("Failed");
            outStream.flush();

            // resetSignup
            this.signup();
        }
    }

    private void updateClientsLocalFiles() throws IOException {
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
            outStream.writeObject("DONE");
        } else {
            outStream.writeObject("NotFound");
        }


    }

    private void downloadSomething(String imageName, String descriptionName, String userId) throws IOException {

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
            // System.out.println(e.getMessage());
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
                    System.out.println(receivedAcknowledgements);
                } else {
                    if (i != 9) {
                        if (ackObj instanceof String ack && receivedAcknowledgements.contains(ack)) {
                            System.out.println("I reiceved a duplicate ACK. Ignored. -> " + ack);
                            ignoreNext = true;
                            i--; //run again and dont send a new packet
                        } else {
                            System.out.println("Invalid ACK. Resending packet " + i);
                            i--; // resend
                        }
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
    }

    private boolean uploadHandshake() throws IOException, ClassNotFoundException {
        String handshakeResponse = (String) inStream.readObject();
        if (handshakeResponse.equals("request to upload")) {
            outStream.writeObject("acceptedUpload");
            return true;
        } else {
            outStream.writeObject("rejected");
            return false;
        }
    }

    private void checkNotifications() throws IOException, ClassNotFoundException {

        ArrayList<String> notifications = new ArrayList<String>();
        String notificationsPath = "server/directories/directory_" + GroupId + clientId + "/notifications.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(notificationsPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                notifications.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        outStream.writeObject(notifications);

        ArrayList<String> responses = new ArrayList<String>();

        if (!notifications.isEmpty()) {
            for (String notification : notifications) {
                responses.add((String) inStream.readObject());
            }
        }
        //_________Exoume gemisei to responses kai prepei kati na to kanoume

    }
}