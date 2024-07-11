import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main {

    private static final String HTTP_200_EMPTY_RESPONSE = "HTTP/1.1 200 OK\r\n\r\n";
    private static final String HTTP_404_EMPTY_RESPONSE = "HTTP/1.1 404 Not Found\r\n\r\n";

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

            var httpRequest = parseHttpRequest(clientSocket);

            var response = getString(httpRequest);

            clientSocket.getOutputStream().write(response);
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static byte[] getString(HttpRequest httpRequest) {
        String response;

        if (httpRequest.getTarget().equals("/")) {
            response = HTTP_200_EMPTY_RESPONSE;
        } else if (httpRequest.getTarget().startsWith("/echo/")) {
            var content = httpRequest.getTarget().substring(6);
            response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    String.format("Content-Length: %d\r\n", content.length()) +
                    "\r\n" +
                    content;
        } else {
            response = HTTP_404_EMPTY_RESPONSE;
        }

        return response.getBytes(StandardCharsets.UTF_8);
    }

    private static HttpRequest parseHttpRequest(Socket socket) throws IOException {
        var requestContent = getRequestContent(socket);

        var lines = requestContent.split("\r\n");
        var requestLine = parseRequestLine(lines[0]);

        var httpRequest = new HttpRequest();
        httpRequest.setMethod(requestLine[0]);
        httpRequest.setTarget(requestLine[1]);
        httpRequest.setVersion(requestLine[2]);

        return httpRequest;
    }

    private static String[] parseRequestLine(String requestLine) {
        return requestLine.split(" ");
    }

    private static String getRequestContent(Socket socket) throws IOException {
        byte[] buffer = new byte[1024];

        StringBuilder sb = new StringBuilder();
        while (true) {
            int bytesRead = socket.getInputStream().read(buffer);
            if (bytesRead > 0) {
                sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
            if (socket.getInputStream().available() == 0) {
                break;
            }
        }

        return sb.toString();
    }

    static class HttpRequest {
        private String method;
        private String target;
        private String version;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
    }
}
