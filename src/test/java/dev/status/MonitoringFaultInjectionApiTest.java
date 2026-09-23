package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoringFaultInjectionApiTest extends MonitoringApiTest {

    @Test
    void given_serviceUp_when_flippedDown_then_transitionHistoryAndFailures() throws Exception {
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl());

            awaitStatus(id, "up", 15);
            mvc.perform(get("/api/v1/services/" + id))
                    .andExpect(jsonPath("$.status").value("up"))
                    .andExpect(jsonPath("$.consecutiveFailures").value(0))
                    .andExpect(jsonPath("$.latencyMs").isNumber());

            fake.setMode("down");
            awaitStatus(id, "down", 15);
            mvc.perform(get("/api/v1/services/" + id))
                    .andExpect(jsonPath("$.status").value("down"))
                    .andExpect(jsonPath("$.consecutiveFailures").isNumber())
                    .andExpect(jsonPath("$.consecutiveFailures").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

            // transition persisted to history (latest is up -> down)
            mvc.perform(get("/api/v1/services/" + id + "/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items").isNotEmpty())
                    .andExpect(jsonPath("$.items[0].to").value("down"));

            fake.setMode("up");
            awaitStatus(id, "up", 15);
            mvc.perform(get("/api/v1/services/" + id))
                    .andExpect(jsonPath("$.status").value("up"))
                    .andExpect(jsonPath("$.consecutiveFailures").value(0));
        }
    }

    private String register(String healthUrl) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(unique("svc"), "Service", "dev", healthUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void awaitStatus(String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        String current = null;
        while (System.currentTimeMillis() < deadline) {
            MvcResult r = mvc.perform(get("/api/v1/services/" + id)).andReturn();
            if (r.getResponse().getStatus() == 200) {
                current = JsonPath.read(r.getResponse().getContentAsString(), "$.status");
                if (expected.equals(current)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("service " + id + " did not reach status " + expected
                + " (last observed: " + current + ")");
    }
}
