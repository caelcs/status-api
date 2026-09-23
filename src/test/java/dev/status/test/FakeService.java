package dev.status.test;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A real in-process HTTP fixture implementing the monitored-service /health
 * contract (api-contract §5). Toggles up/degraded/down/slow and counts probes.
 */
public final class FakeService implements AutoCloseable {

    private final HttpServer server;
    private final AtomicReference<String> mode = new AtomicReference<>("up");
    private final AtomicInteger probeCount = new AtomicInteger(0);

    public FakeService() {
        this("up");
    }

    public FakeService(String initialMode) {
        this.mode.set(initialMode);
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/health", this::health);
            server.createContext("/__fault", this::fault);
            server.start();
        } catch (Exception e) {
            throw new IllegalStateException("failed to start fake service", e);
        }
    }

    private void health(com.sun.net.httpserver.HttpExchange ex) throws java.io.IOException {
        probeCount.incrementAndGet();
        String m = mode.get();
        if ("down".equals(m)) {
            byte[] body = "{\"error\":\"service down\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(500, body.length);
            ex.getResponseBody().write(body);
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
        byte[] body = ("{\"status\":\"" + status + "\",\"version\":\"1.0.0\"}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private void fault(com.sun.net.httpserver.HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String newMode = parseMode(body);
        if (newMode != null) {
            mode.set(newMode);
        }
        byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, resp.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(resp);
        }
    }

    private static String parseMode(String body) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"mode\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    public String healthUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/health";
    }

    public String faultUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/__fault";
    }

    public void setMode(String newMode) {
        mode.set(newMode);
    }

    public int probeCount() {
        return probeCount.get();
    }

    public void resetProbeCount() {
        probeCount.set(0);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
