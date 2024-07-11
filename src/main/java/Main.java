import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    private static final String HTTP_200_EMPTY_RESPONSE = "HTTP/1.1 200 OK\r\n\r\n";

    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");

        ServerSocket serverSocket = null;
        Socket clientSocket = null;

        try {
            serverSocket = new ServerSocket(4221);

            // Since the tester restarts your program quite often, setting SO_REUSEADDR
            // ensures that we don't run into 'Address already in use' errors
            serverSocket.setReuseAddress(true);
            clientSocket = serverSocket.accept(); // Wait for connection from client.

            var outputStream = clientSocket.getOutputStream();
            outputStream.write(HTTP_200_EMPTY_RESPONSE.getBytes());
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
