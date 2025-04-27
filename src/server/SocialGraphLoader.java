package server;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SocialGraphLoader {
    private final Map<String, List<String>> graph;
    private final String socialGraphPath = "../../data/SocialGraph.txt";

    public SocialGraphLoader() throws IOException {
        this.graph = new HashMap<>();
    }

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

    public String[] getFollowers(String clientId) throws FileNotFoundException, IOException {
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
            reader.close();
        }
        return null;
    }

    public ArrayList<String> getFollowing(String clientId) throws FileNotFoundException, IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(socialGraphPath))) {
            String line;
            ArrayList<String> following = new ArrayList<String>();

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

    public Set<String> getAllUsers() {
        return graph.keySet();
    }

    public void printGraph() {
        for (String user : graph.keySet()) {
            System.out.println(user + " follows " + graph.get(user));
        }
    }
}