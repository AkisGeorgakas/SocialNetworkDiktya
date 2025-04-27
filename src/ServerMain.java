import server.SocialGraphLoader;
import server.UsersLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ServerMain {
    public static void main(String[] args) {
        try {
            SocialGraphLoader socialLoader = new SocialGraphLoader();
            String[] followers = socialLoader.getFollowers("5566");
            System.out.println("Followers of");
            for(String s : followers) {
                System.out.println(s);
            }

            System.out.println("Following");
            ArrayList<String> following = socialLoader.getFollowing("9078");
            for(String s : following) {
                System.out.println(s);
            }

        } catch (IOException e) {
            System.err.println("Error loading SocialGraph: " + e.getMessage());
        }
    }
}