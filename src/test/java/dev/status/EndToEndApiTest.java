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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S48 / AC12 / AC13: a genuine end-to-end API-first test over real HTTP against
 * a running application instance backed by Testcontainers Postgres. Registers a
 * service, lets a real fake service be probed, observes the status transition
 * over SSE, and asserts persisted state + history through the HTTP API only.
 */
class EndToEndApiTest extends RealServerMonitoringApiTest {

    @Test
    void given_registeredService_when_probedAndFaultInjected_then_transitionObservedOnSse_and_statePersisted() throws Exception {
        try (FakeService fake = new FakeService("up")) {
            // Given: a service registered against a real, probed health endpoint
            String id = register(fake.healthUrl());
            awaitStatus(id, "up", 15);

            // Given: a live SSE client connected to the events stream
            CompletableFuture<String> sse = openSse("dev");
            Thread.sleep(600); // let the SSE connection establish

            // When: the monitored service fails
            fake.setMode("down");

            // Then: the transition is observed on the SSE stream
            String sseBody = sse.get(15000, TimeUnit.MILLISECONDS);
            assertThat(sseBody)
                    .contains("status.changed")
                    .contains("\"to\":\"down\"");

            // Then: the persisted state reflects the transition (via HTTP API only)
            awaitStatus(id, "down", 15);
            HttpResponse<String> status = get("/api/v1/services/" + id);
            assertThat(status.statusCode()).isEqualTo(200);
            assertThat((String) JsonPath.read(status.body(), "$.status")).isEqualTo("down");
            assertThat((Integer) JsonPath.read(status.body(), "$.consecutiveFailures"))
                    .isGreaterThanOrEqualTo(1);

            // Then: the transition is persisted to history (via HTTP API only)
            HttpResponse<String> history = get("/api/v1/services/" + id + "/history");
            assertThat(history.statusCode()).isEqualTo(200);
            List<Object> toStatuses = JsonPath.read(history.body(), "$.items[*].to");
            assertThat(toStatuses).contains("down");
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

    private CompletableFuture<String> openSse(String env) {
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
