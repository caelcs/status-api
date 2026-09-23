package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR9 / AC10: toggling a mock service via POST /__fault turns its cell red
 * (down) within one check interval, and the counters update.
 */
class MockServiceFaultInjectionTest extends RealServerMonitoringApiTest {

    @Test
    void given_mockService_when_faultToggledDown_then_cellRed_and_countersUpdate() throws Exception {
        String tag = unique("fault");
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl(), tag);
            awaitStatus(id, "up", 15);

            // fault-inject via the mock service's control endpoint
            HttpResponse<String> fault = http.send(
                    HttpRequest.newBuilder(URI.create(fake.faultUrl()))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"mode\":\"down\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(fault.statusCode()).isEqualTo(200);

            // cell red within one check interval (<15s)
            awaitStatus(id, "down", 15);

            // counters update
            HttpResponse<String> list = get("/api/v1/services?env=dev&tag=" + tag);
            int down = JsonPath.read(list.body(), "$.summary.down");
            assertThat(down).isEqualTo(1);
            int up = JsonPath.read(list.body(), "$.summary.up");
            assertThat(up).isZero();
        }
    }

    private String register(String healthUrl, String tag) throws Exception {
        HttpResponse<String> r = post("/api/v1/services",
                "{\"key\":\"" + unique("svc") + "\",\"name\":\"Service\",\"env\":\"dev\","
                        + "\"healthUrl\":\"" + healthUrl + "\",\"tags\":[\"" + tag + "\"]}",
                TestKeys.DEV_KEY);
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
}
