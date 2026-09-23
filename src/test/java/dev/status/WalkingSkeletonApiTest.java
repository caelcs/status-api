package dev.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalkingSkeletonApiTest extends BaseApiTest {

    @Test
    void given_boot_when_health_then_200() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void given_emptyDb_when_listServices_then_200EmptyList() throws Exception {
        mvc.perform(get("/api/v1/services").param("env", unique("empty")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.summary.up").value(0))
                .andExpect(jsonPath("$.summary.degraded").value(0))
                .andExpect(jsonPath("$.summary.down").value(0))
                .andExpect(jsonPath("$.summary.unknown").value(0));
    }

    @Test
    void given_missingKey_when_postService_then_401Exact() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", TestKeys.DEV_ENV, "http://payments:8080/health")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Invalid or missing API key\"}",
                        true));
    }

    @Test
    void given_unknownKey_when_postService_then_401Exact() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", "does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", TestKeys.DEV_ENV, "http://payments:8080/health")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Invalid or missing API key\"}",
                        true));
    }

    @Test
    void given_devKey_when_postProdEnv_then_403Exact() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", TestKeys.PROD_ENV, "http://payments:8080/health")))
                .andExpect(status().isForbidden())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"API key is not authorized for environment prod\"}",
                        true));
    }
}
