import com.nebs.api.ClientOptions;
import com.nebs.api.NebsClient;
import com.nebs.api.PlayerStatus;
import com.nebs.api.ScreenshotInfo;

/**
 * The same as hello.main.kts, in Java.
 *
 *   ./gradlew :api:allJar
 *   java -cp api/build/libs/nebs-api-0.1.0-all.jar examples/HelloNebs.java [host] [port]
 */
public class HelloNebs {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25565;

        try (NebsClient client = new NebsClient(new ClientOptions().name("Javy"))) {
            client.connect(host, port);
            PlayerStatus start = client.status();
            System.out.println(client.getName() + " joined at " + start.getBlockX() + " " + start.getBlockY() + " " + start.getBlockZ());

            client.chat("Hello from Java!");
            client.walkTo(start.getBlockX() - 5, start.getBlockZ() - 5);
            client.lookAt(start.getX(), start.getY() + 1.6, start.getZ());

            ScreenshotInfo shot = client.screenshot("hello-from-java");
            System.out.println("Screenshot: " + shot.getPath());
        } // closing the client quits the game
    }
}
