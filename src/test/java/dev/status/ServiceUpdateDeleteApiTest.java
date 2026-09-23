package dev.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceUpdateDeleteApiTest extends BaseApiTest {

    @Test
    void given_existingService_when_put_then_200AndUpdated() throws Exception {
        String key = unique("payments");
        String id = registerAndGetId(key, "Payments", "dev", TestKeys.DEV_KEY);

        mvc.perform(put("/api/v1/services/" + id)
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Payments v2", "dev", "http://payments:9090/health")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Payments v2"))
                .andExpect(jsonPath("$.healthUrl").value("http://payments:9090/health"));

        mvc.perform(get("/api/v1/services/" + id))
                .andExpect(jsonPath("$.name").value("Payments v2"))
                .andExpect(jsonPath("$.healthUrl").value("http://payments:9090/health"));
    }

    @Test
    void given_existingService_when_putMalformed_then_400() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(put("/api/v1/services/" + id)
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"bad!\",\"env\":\"dev\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void given_existingService_when_putMissingKey_then_401() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(put("/api/v1/services/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "dev", "http://a:1/health")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void given_devService_when_putProdKey_then_403() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(put("/api/v1/services/" + id)
                        .header("X-API-Key", TestKeys.PROD_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "prod", "http://a:1/health")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void given_unknownId_when_put_then_404() throws Exception {
        mvc.perform(put("/api/v1/services/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("payments", "Payments", "dev", "http://a:1/health")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void given_existingService_when_delete_then_204() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(delete("/api/v1/services/" + id)
                        .header("X-API-Key", TestKeys.DEV_KEY))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/services/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void given_existingService_when_deleteMissingKey_then_401() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(delete("/api/v1/services/" + id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void given_devService_when_deleteProdKey_then_403() throws Exception {
        String id = registerAndGetId(unique("payments"), "Payments", "dev", TestKeys.DEV_KEY);
        mvc.perform(delete("/api/v1/services/" + id)
                        .header("X-API-Key", TestKeys.PROD_KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void given_unknownId_when_delete_then_404() throws Exception {
        mvc.perform(delete("/api/v1/services/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", TestKeys.DEV_KEY))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetId(String key, String name, String env, String apiKey) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, name, env, "http://" + key + ":8080/health")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
