import server.UsersLoader;

import java.io.IOException;
import java.util.List;

public class ServerMain {
    public static void main(String[] args) {
        try {
            UsersLoader loader = new UsersLoader("../data/users.txt");

            List<String> info = loader.getUserInfo("alice");
            System.out.println("Alice's info: " + info);
            System.out.println("=========================");
            loader.printUsers();

        } catch (IOException e) {
            System.err.println("Error loading SocialGraph: " + e.getMessage());
        }
    }
}