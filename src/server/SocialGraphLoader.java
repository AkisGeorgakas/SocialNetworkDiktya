package server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SocialGraphLoader {
  
    private final Map<String, List<String>> graph;
    private final String socialGraphPath = "../data/SocialGraph.txt";

    public SocialGraphLoader() throws IOException {
        this.graph = new HashMap<>();
    }

    // TODO: to be removed
    private void loadGraph() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":");

                if(parts.length >= 1) {
                    String user = parts[0].trim();
                    List<String> follows = new ArrayList<>();

                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        String[] followArray = parts[1].split(",");
                        for (String f : followArray) {
                            follows.add(f.trim());
                        }
                    }
    
                    graph.put(user, follows);
                }
                else {
                    System.out.println("Empty line.");
                }
            }
        }
    }

    // returns String[] of the people that follow given client
    public String[] getFollowers(String clientId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] all = line.split(" ");

                if(all.length >= 1 && all[0].equals(clientId)) {
                    String[] parts = new String[all.length - 1];

                    System.arraycopy(all, 1, parts, 0, all.length - 1);

                    return parts;
                }
            }
        }
        return null;
    }

    // returns ArrayList<String> of the people that given client follows
    public ArrayList<String> getFollowing(String clientId) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {
            String line;
            ArrayList<String> following = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                String[] all = line.split(" ");

                if(all.length >= 1 && !all[0].equals(clientId)) {
                    for(int i=1; i<all.length; i++) {
                        if(all[i].equals(clientId)) following.add(all[0]);
                    }
                }
            }
            reader.close();

            return following;
        }
    }

    // TODO: to be removed
    public Set<String> getAllUsers() {
        return graph.keySet();
    }

    
    // TODO: to be removed
    public void printGraph() {
        for (String user : graph.keySet()) {
            System.out.println(user + " follows " + graph.get(user));
        }
    }

    public void acceptFollowRequests(String clientId, ArrayList<String> acceptFrom) throws IOException {

        Path path = Paths.get(socialGraphPath);
        List<String> lines = Files.readAllLines(path);
        List<String> updatedLines = new ArrayList<>();

        boolean updated = false;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");

            if (parts.length > 0 && parts[0].equals(clientId)) {

                StringBuilder lineBuilder = new StringBuilder(line);
                for(String followerID : acceptFrom) {
                    // Check if follower already exists to avoid duplicates
                    boolean alreadyFollowed = Arrays.asList(parts).contains(followerID);
                    if (!alreadyFollowed) {
                        lineBuilder.append(" ").append(followerID);
                    }
                    updated = true;

                }
                line = lineBuilder.toString();
            }
            updatedLines.add(line);
        }

        // If the targetClientID was not found, optionally add it as a new line

        if (!updated) {
            StringBuilder newLine = new StringBuilder(clientId);
            for(String followerID : acceptFrom){
                newLine.append(" ").append(followerID);
            }
            updatedLines.add(newLine.toString());
        }

        // Rewrite the file
        Files.write(path, updatedLines);

    }


}