package dev.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceRegistrationApiTest extends BaseApiTest {

    @Test
    void given_validKeyAndEnv_when_postService_then_201AndRoundTrip() throws Exception {
        String key = unique("payments");
        String body = "{\"key\":\"" + key + "\",\"name\":\"Payments\",\"env\":\"dev\","
                + "\"description\":\"Payment processing\",\"team\":\"checkout\","
                + "\"healthUrl\":\"http://payments:8080/health\",\"tags\":[\"critical\",\"pci\"]}";

        String created = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").value("Payments"))
                .andExpect(jsonPath("$.env").value("dev"))
                .andExpect(jsonPath("$.description").value("Payment processing"))
                .andExpect(jsonPath("$.team").value("checkout"))
                .andExpect(jsonPath("$.healthUrl").value("http://payments:8080/health"))
                .andExpect(jsonPath("$.tags[0]").value("critical"))
                .andExpect(jsonPath("$.tags[1]").value("pci"))
                .andExpect(jsonPath("$.status").value("unknown"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

        // field-by-field read-back by id
        mvc.perform(get("/api/v1/services/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.key").value(key))
                .andExpect(jsonPath("$.name").value("Payments"))
                .andExpect(jsonPath("$.env").value("dev"))
                .andExpect(jsonPath("$.description").value("Payment processing"))
                .andExpect(jsonPath("$.team").value("checkout"))
                .andExpect(jsonPath("$.healthUrl").value("http://payments:8080/health"))
                .andExpect(jsonPath("$.tags[0]").value("critical"))
                .andExpect(jsonPath("$.tags[1]").value("pci"))
                .andExpect(jsonPath("$.status").value("unknown"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0));
    }

    @Test
    void given_existingService_when_rePostSameKeyAndEnv_then_409_noDuplicate() throws Exception {
        String key = unique("payments");
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Payments", "dev", "http://payments:8080/health")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Payments", "dev", "http://payments:8080/health")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409));

        // no duplicate row was created
        mvc.perform(get("/api/v1/services").param("env", "dev"))
                .andExpect(jsonPath("$.items[?(@.key == '" + key + "')]", hasSize(1)));
    }

    @Test
    void given_existingKey_when_postDifferentService_then_409() throws Exception {
        String key = unique("payments");
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Payments", "dev", "http://a:1/health")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Other", "dev", "http://b:2/health")))
                .andExpect(status().isConflict());
    }

    @Test
    void given_malformedBody_when_post_then_400WithErrors() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"UPPER!\",\"name\":\"\",\"env\":\"dev\",\"healthUrl\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void given_unknownKey_when_post_then_401Exact() throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "dev", "http://a:1/health")))
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
                        .content(registration("payments", "Payments", "prod", "http://a:1/health")))
                .andExpect(status().isForbidden())
                .andExpect(content().json(
                        "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"API key is not authorized for environment prod\"}",
                        true));
    }
}
