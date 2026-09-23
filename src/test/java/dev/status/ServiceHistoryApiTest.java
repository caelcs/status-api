package dev.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceHistoryApiTest extends BaseApiTest {

    @Test
    void given_existingService_when_getHistory_then_200Empty() throws Exception {
        String id = registerAndGetId(unique("payments"), "dev");
        mvc.perform(get("/api/v1/services/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void given_existingService_when_getHistoryWithSinceUntilLimit_then_200() throws Exception {
        String id = registerAndGetId(unique("payments"), "dev");
        mvc.perform(get("/api/v1/services/" + id + "/history")
                        .param("since", "2020-01-01T00:00:00Z")
                        .param("until", "2030-01-01T00:00:00Z")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.since").isNotEmpty())
                .andExpect(jsonPath("$.until").isNotEmpty());
    }

    @Test
    void given_unknownId_when_getHistory_then_404() throws Exception {
        mvc.perform(get("/api/v1/services/00000000-0000-0000-0000-000000000000/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private String registerAndGetId(String key, String env) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Service " + key, env, "http://" + key + ":8080/health")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
