import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class Main {

    private static final byte[] HTTP_200_EMPTY_RESPONSE = "HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTTP_201_EMPTY_RESPONSE = "HTTP/1.1 201 Created\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HTTP_404_EMPTY_RESPONSE = "HTTP/1.1 404 Not Found\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    private static String filesDirectory = "";

    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");

        filesDirectory = getFileDirectory(args);

        try {
            try (ServerSocket serverSocket = new ServerSocket(4221)) {

                // Since the tester restarts your program quite often, setting SO_REUSEADDR
                // ensures that we don't run into 'Address already in use' errors
                serverSocket.setReuseAddress(true);

                try (var executors = Executors.newVirtualThreadPerTaskExecutor()) {
                    while (true) {
                        executors.submit(() -> handleHttpRequest(serverSocket));
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static String getFileDirectory(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--directory") && i + 1 < args.length) {
                return args[i + 1];
            }
        }

        return "";
    }

    private static void handleHttpRequest(ServerSocket serverSocket) {
        try {
            // Wait for connection from client.
            var clientSocket = serverSocket.accept();

            var httpRequest = parseHttpRequest(clientSocket);
            writeResponse(clientSocket.getOutputStream(), httpRequest);
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void writeResponse(OutputStream outputStream, HttpRequest httpRequest) throws IOException {

        if (httpRequest.getTarget().equals("/")) {
            outputStream.write(HTTP_200_EMPTY_RESPONSE);
        } else if (httpRequest.getTarget().equalsIgnoreCase("/user-agent")) {
            var content = httpRequest.getHeaders().getOrDefault("User-Agent", "");
            var response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    String.format("Content-Length: %d\r\n", content.length()) +
                    "\r\n" +
                    content;
            outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        } else if (httpRequest.getTarget().startsWith("/echo/")) {
            var content = httpRequest.getTarget().substring(6);
            var encodingHeader = httpRequest.getHeaders().getOrDefault("Accept-Encoding", "");
            var encodings = Arrays.stream(encodingHeader.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            if (encodings.contains("gzip")) {
                var compressed = gzipCompress(content);
                var response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Encoding: gzip\r\n" +
                        String.format("Content-Length: %d\r\n", compressed.length) +
                        "\r\n";
                outputStream.write(response.getBytes(StandardCharsets.UTF_8));
                outputStream.write(compressed);
            } else {
                var response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain\r\n" +
                        String.format("Content-Length: %d\r\n", content.length()) +
                        "\r\n" +
                        content;
                outputStream.write(response.getBytes(StandardCharsets.UTF_8));
            }

        } else if (httpRequest.getTarget().startsWith("/files/")) {
            var fileName = httpRequest.getTarget().substring(7);
            var filePath = Paths.get(filesDirectory, fileName);

            if (httpRequest.getMethod().equals("GET")) {
                if (Files.exists(filePath)) {
                    var content = Files.readString(filePath);
                    var response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            String.format("Content-Length: %d\r\n", content.length()) +
                            "\r\n" +
                            content;
                    outputStream.write(response.getBytes(StandardCharsets.UTF_8));
                } else {
                    outputStream.write(HTTP_404_EMPTY_RESPONSE);
                }
            } else if (httpRequest.getMethod().equals("POST")) {
                Files.writeString(filePath, httpRequest.getBody());
                outputStream.write(HTTP_201_EMPTY_RESPONSE);
            } else {
                outputStream.write(HTTP_404_EMPTY_RESPONSE);
            }
        } else {
            outputStream.write(HTTP_404_EMPTY_RESPONSE);
        }
    }

    private static byte[] gzipCompress(String content) {

        try {
            try (var bos = new ByteArrayOutputStream();
                 var gzip = new GZIPOutputStream(bos)) {
                gzip.write(content.getBytes(StandardCharsets.UTF_8));
                gzip.finish();
                return bos.toByteArray();
            }
        } catch (IOException e) {
            throw new RuntimeException("Gzip compress failed", e);
        }
    }

    private static HttpRequest parseHttpRequest(Socket socket) throws IOException {
        var requestContent = getRequestContent(socket);

        var lines = requestContent.split("\r\n");
        var requestLine = parseRequestLine(lines[0]);

        var httpRequest = new HttpRequest();
        httpRequest.setMethod(requestLine[0]);
        httpRequest.setTarget(requestLine[1]);
        httpRequest.setVersion(requestLine[2]);

        Map<String, String> headers = parseHeaders(lines);
        httpRequest.setHeaders(headers);

        if (httpRequest.getHeaders().containsKey("Content-Type")) {
            httpRequest.setBody(lines[lines.length - 1]);
        }

        return httpRequest;
    }

    private static Map<String, String> parseHeaders(String[] lines) {
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            var tokens = lines[i].split(":");
            if (tokens.length == 2) {
                headers.put(tokens[0].trim(), tokens[1].trim());
            }
        }
        return headers;
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
        private Map<String, String> headers;
        private String body;

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

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
