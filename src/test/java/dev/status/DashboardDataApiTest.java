package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard data contract: one GET /services?env= returns the matrix AND the
 * summary counters (FR8).
 */
class DashboardDataApiTest extends MonitoringApiTest {

    @Test
    void given_services_when_getServices_then_matrixAndSummaryInOneCall() throws Exception {
        String tag = unique("dash");
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl(), tag);
            awaitStatus(id, "up", 15);

            mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("up"))
                    .andExpect(jsonPath("$.summary.total").value(1))
                    .andExpect(jsonPath("$.summary.up").value(1));
        }
    }

    private String register(String healthUrl, String tag) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + unique("svc") + "\",\"name\":\"Service\",\"env\":\"dev\","
                                + "\"healthUrl\":\"" + healthUrl + "\",\"tags\":[\"" + tag + "\"]}"))
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
