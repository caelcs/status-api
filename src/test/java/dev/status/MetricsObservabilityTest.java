package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsObservabilityTest extends MonitoringApiTest {

    @Test
    void given_checksRun_when_prometheus_then_metricsExposed() throws Exception {
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl());
            awaitStatus(id, "up", 15);

            mvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("status_checks_total")))
                    .andExpect(content().string(containsString("status_check_duration_seconds")))
                    .andExpect(content().string(containsString("status_up")))
                    .andExpect(content().string(containsString("status_claims_total")))
                    .andExpect(content().string(containsString("status_inflight_checks")))
                    .andExpect(content().string(containsString("status_transitions_total")));
        }
    }

    private String register(String healthUrl) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(registration(unique("svc"), "Service", "dev", healthUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void awaitStatus(String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            MvcResult r = mvc.perform(get("/api/v1/services/" + id)).andReturn();
            if (r.getResponse().getStatus() == 200) {
                String current = JsonPath.read(r.getResponse().getContentAsString(), "$.status");
                if (expected.equals(current)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("service did not reach status " + expected);
    }
}
