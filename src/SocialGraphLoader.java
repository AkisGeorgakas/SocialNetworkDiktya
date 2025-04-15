import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SocialGraphLoader {
    private final Map<String, List<String>> graph;

    public SocialGraphLoader(String filepath) throws IOException {
        this.graph = new HashMap<>();
        loadGraph(filepath);
    }

    private void loadGraph(String filepath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
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

    public List<String> getFollowedUsers(String username) {
        return graph.getOrDefault(username, new ArrayList<>());
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