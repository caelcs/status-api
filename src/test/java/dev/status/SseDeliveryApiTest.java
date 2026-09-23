package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SseDeliveryApiTest extends RealServerMonitoringApiTest {

    @Test
    void given_connectedClient_when_transition_then_eventReceived() throws Exception {
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl());
            awaitStatus(id, "up", 15);

            CompletableFuture<String> sse = startSseReader("dev");
            Thread.sleep(600);            // let the SSE connection establish
            fake.setMode("down");         // trigger the transition

            String body = sse.get(15000, TimeUnit.MILLISECONDS);
            assertThat(body)
                    .contains("status.changed")
                    .contains("\"to\":\"down\"");
        }
    }

    private String register(String healthUrl) throws Exception {
        HttpResponse<String> r = post("/api/v1/services",
                registration(unique("svc"), "Service", "dev", healthUrl), TestKeys.DEV_KEY);
        assertThat(r.statusCode()).isEqualTo(201);
        return JsonPath.read(r.body(), "$.id");
    }

    private void awaitStatus(String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> r = get("/api/v1/services/" + id);
            if (r.statusCode() == 200) {
                String current = JsonPath.read(r.body(), "$.status");
                if (expected.equals(current)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("service did not reach status " + expected);
    }

    private CompletableFuture<String> startSseReader(String env) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/events?env=" + env))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                    if (line.contains("\"to\":\"down\"")) {
                        break;
                    }
                }
                future.complete(sb.toString());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        t.setDaemon(true);
        t.start();
        return future;
    }
}
