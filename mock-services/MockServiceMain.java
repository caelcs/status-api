import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny mock monitored-service: GET /health (ok|degraded|down|slow) + POST
 * /__fault {mode:...}. Self-registers with status-api on startup.
 *
 * Run via `java MockServiceMain.java` with the env vars below.
 */
public class MockServiceMain {

    public static void main(String[] args) throws Exception {
        String name = env("MOCK_NAME", "mock");
        String env = env("MOCK_ENV", "dev");
        String apiKey = env("MOCK_API_KEY", "");
        int port = Integer.parseInt(env("MOCK_PORT", "8080"));
        String selfHost = env("MOCK_SELF_HOST", name);
        String statusApiUrl = env("STATUS_API_URL", "http://localhost:8080");

        AtomicReference<String> mode = new AtomicReference<>("up");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", ex -> {
            String m = mode.get();
            if ("down".equals(m)) {
                byte[] b = "{\"error\":\"down\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(500, b.length);
                ex.getResponseBody().write(b);
                ex.close();
                return;
            }
            if ("slow".equals(m)) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            String status = "degraded".equals(m) ? "degraded" : "ok";
            byte[] b = ("{\"status\":\"" + status + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.createContext("/__fault", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"mode\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (m.find()) {
                mode.set(m.group(1));
            }
            byte[] b = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();

        String healthUrl = "http://" + selfHost + ":" + port + "/health";
        String regBody = "{\"key\":\"" + name + "\",\"name\":\"" + name + "\",\"env\":\"" + env
                + "\",\"healthUrl\":\"" + healthUrl + "\",\"tags\":[\"mock\"]}";

        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < 30; i++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(statusApiUrl + "/api/v1/services"))
                        .header("Content-Type", "application/json")
                        .header("X-API-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(regBody))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 201 || resp.statusCode() == 409) {
                    System.out.println(name + " registered (" + resp.statusCode() + ")");
                    break;
                }
            } catch (Exception e) {
                // retry until status-api is reachable
            }
            Thread.sleep(1000);
        }

        Thread.currentThread().join();
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return v == null ? defaultValue : v;
    }
}
