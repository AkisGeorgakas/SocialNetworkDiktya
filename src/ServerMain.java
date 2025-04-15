import java.io.IOException;
import java.util.List;

public class ServerMain {
    public static void main(String[] args) {
        try {
            SocialGraphLoader loader = new SocialGraphLoader("../data/SocialGraph.txt");

            List<String> followed = loader.getFollowedUsers("alice");
            System.out.println("Alice follows: " + followed);

            loader.printGraph();

        } catch (IOException e) {
            System.err.println("Error loading SocialGraph: " + e.getMessage());
        }
    }
}