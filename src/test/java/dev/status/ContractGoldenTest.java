package dev.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the api-contract.md JSON schemas (ServiceStatus, ServiceList,
 * StatusHistory) and the exact 401/403 problem-details payloads.
 */
class ContractGoldenTest extends BaseApiTest {

    @Test
    void given_registeredService_when_getService_then_serviceStatusSchema() throws Exception {
        String key = unique("payments");
        String id = register(key);

        mvc.perform(get("/api/v1/services/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.env").value("dev"))
                .andExpect(jsonPath("$.healthUrl").isNotEmpty())
                .andExpect(jsonPath("$.status").value("unknown"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0))
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void given_registeredService_when_list_then_serviceListSchema() throws Exception {
        String key = unique("payments");
        register(key);

        mvc.perform(get("/api/v1/services").param("env", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.summary.total").isNumber())
                .andExpect(jsonPath("$.summary.up").isNumber())
                .andExpect(jsonPath("$.summary.degraded").isNumber())
                .andExpect(jsonPath("$.summary.down").isNumber())
                .andExpect(jsonPath("$.summary.unknown").isNumber())
                .andExpect(jsonPath("$.limit").isNumber())
                .andExpect(jsonPath("$.offset").isNumber());
    }

    @Test
    void given_registeredService_when_history_then_statusHistorySchema() throws Exception {
        String id = register(unique("payments"));
        mvc.perform(get("/api/v1/services/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void given_missingKey_when_post_then_401ExactEnvelope() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "dev", "http://a:1/health")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Invalid or missing API key\"}",
                        true));
    }

    @Test
    void given_devKey_when_postProd_then_403ExactEnvelope() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "prod", "http://a:1/health")))
                .andExpect(status().isForbidden())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"API key is not authorized for environment prod\"}",
                        true));
    }

    private String register(String key) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Service " + key, "dev", "http://" + key + ":8080/health")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
