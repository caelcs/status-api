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

/**
 * FR7 / AC9: a transition collected by instance A reaches a dashboard connected
 * to instance B via Postgres LISTEN/NOTIFY.
 */
class CrossInstanceNotifyTest {

    @Test
    void given_instanceACollects_when_instanceBDashboard_then_delivered() throws Exception {
        try (AppInstance a = AppInstance.boot(AppInstance.baseProps());
             FakeService fake = new FakeService("up")) {

            // instance A registers and claims the service
            HttpResponse<String> reg = a.post("/api/v1/services",
                    registration(unique("svc"), "Service", "dev", fake.healthUrl()), TestKeys.DEV_KEY);
            String id = JsonPath.read(reg.body(), "$.id");
            awaitStatus(a, id, "up", 15);

            // instance B joins AFTER the service is already owned by A
            try (AppInstance b = AppInstance.boot(AppInstance.baseProps())) {
                CompletableFuture<String> sse = openSse(b, "dev");
                Thread.sleep(600);
                fake.setMode("down"); // collected by A, delivered to B via NOTIFY

                String body = sse.get(15000, TimeUnit.MILLISECONDS);
                assertThat(body)
                        .contains("status.changed")
                        .contains("\"to\":\"down\"");
            }
        }
    }

    private static String registration(String key, String name, String env, String healthUrl) {
        return "{\"key\":\"" + key + "\",\"name\":\"" + name + "\",\"env\":\"" + env
                + "\",\"healthUrl\":\"" + healthUrl + "\"}";
    }

    private static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private static void awaitStatus(AppInstance instance, String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> r = instance.get("/api/v1/services/" + id);
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

    private static CompletableFuture<String> openSse(AppInstance instance, String env) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(instance.url("/api/v1/events?env=" + env)))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<InputStream> resp = instance.http.send(req, HttpResponse.BodyHandlers.ofInputStream());
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
