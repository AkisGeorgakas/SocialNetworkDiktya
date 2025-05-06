package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class UsersLoader {
    private HashMap<String, List<String>> users = new HashMap<>();
    private final String filepath;

    public UsersLoader(String filepath) {
        this.filepath = filepath;
    }

    private void loadUsers() throws IOException {
        try  {
            BufferedReader reader = new BufferedReader(new FileReader(filepath));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":");

                if(parts.length >= 1) {
                    String username = parts[0].trim();
                    List<String> info = new ArrayList<>();

                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        String[] infoArray = parts[1].split(",");
                        for (String f : infoArray) {
                            info.add(f.trim());
                        }
                    }

                    users.put(username, info);
                }
                else {
                    System.out.println("Empty line.");
                }
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getUserInfo(String username) throws IOException {
        loadUsers();
        return users.getOrDefault(username, new ArrayList<>());
    }

    public Set<String> getAllUsers() throws IOException {
        loadUsers();
        return users.keySet();
    }

    // return clientId
    public String checkUser(String username, String password) throws IOException {
        loadUsers();
        List<String> infoToCheck = users.get(username);
        if (infoToCheck != null && Objects.equals(password, infoToCheck.getFirst())) {
            return infoToCheck.getLast();
        }
        return null;

    }

    public void addUser ( String newInfo) throws IOException {
        FileWriter writer = null;
        try{
            writer = new FileWriter(filepath,true);
            writer.append("\n");
            writer.append(newInfo);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
            assert writer != null;
            writer.close();
        }
    }

    public void printUsers() throws IOException {
        loadUsers();
        for (String user : users.keySet()) {
            System.out.println(user + " has info: " + users.get(user));
        }
    }

    public String getUserName(String userId) throws IOException {
      loadUsers();
        for (HashMap.Entry<String, List<String>> user : users.entrySet()) {

          String tempClientId =  user.getValue().getLast();
          if(tempClientId.equals(userId)){
            return user.getKey();
          }

        }
        return "";
  }
}
