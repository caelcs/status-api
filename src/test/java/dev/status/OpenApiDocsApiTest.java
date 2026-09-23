package dev.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S50: proves the springdoc/OpenAPI annotations moved onto the {@code ServiceApi}
 * / {@code SseApi} interfaces still produce the documented operations. Spring
 * inherits interface annotations onto the handler method, so springdoc must keep
 * documenting every operation and its success/error responses. Fetches
 * {@code /v3/api-docs} over HTTP and asserts them.
 */
class OpenApiDocsApiTest extends BaseApiTest {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    @Test
    void given_appBooted_when_fetchApiDocs_then_documentedOperationsAndResponsesPresent() throws Exception {
        Map<String, Object> paths = fetchPaths();

        assertThat(paths).containsKeys(
                "/api/v1/services",
                "/api/v1/services/{id}",
                "/api/v1/services/{id}/history",
                "/api/v1/events");

        assertThat(operations(paths, "/api/v1/services")).containsExactlyInAnyOrder("get", "post");
        assertThat(operations(paths, "/api/v1/services/{id}")).containsExactlyInAnyOrder("get", "put", "delete");
        assertThat(operations(paths, "/api/v1/services/{id}/history")).containsExactlyInAnyOrder("get");
        assertThat(operations(paths, "/api/v1/events")).containsExactlyInAnyOrder("get");

        assertThat(responses(paths, "/api/v1/services", "get")).containsExactlyInAnyOrder("200", "400");
        assertThat(responses(paths, "/api/v1/services", "post")).containsExactlyInAnyOrder("201", "400", "401", "403", "409");
        assertThat(responses(paths, "/api/v1/services/{id}", "get")).containsExactlyInAnyOrder("200", "404");
        assertThat(responses(paths, "/api/v1/services/{id}", "put")).containsExactlyInAnyOrder("200", "400", "401", "403", "404");
        assertThat(responses(paths, "/api/v1/services/{id}", "delete")).containsExactlyInAnyOrder("204", "401", "403", "404");
        assertThat(responses(paths, "/api/v1/services/{id}/history", "get")).containsExactlyInAnyOrder("200", "404");
        assertThat(responses(paths, "/api/v1/events", "get")).containsExactlyInAnyOrder("200");
    }

    @Test
    void given_appBooted_when_fetchApiDocs_then_interfaceParameterAnnotationsInherited() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> listOperation = JsonPath.read(body, "$.paths['/api/v1/services'].get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) listOperation.get("parameters");

        Map<String, Object> envParam = parameters.stream()
                .filter(p -> "env".equals(p.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("list operation must document the env parameter"));

        assertThat(envParam.get("description")).isEqualTo("Environment (required)");
    }

    private Map<String, Object> fetchPaths() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.paths");
    }

    @SuppressWarnings("unchecked")
    private static Set<String> operations(Map<String, Object> paths, String path) {
        Map<String, Object> item = (Map<String, Object>) paths.get(path);
        Set<String> methods = new HashSet<>();
        for (String key : item.keySet()) {
            if (HTTP_METHODS.contains(key)) {
                methods.add(key);
            }
        }
        return methods;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> responses(Map<String, Object> paths, String path, String method) {
        Map<String, Object> item = (Map<String, Object>) paths.get(path);
        Map<String, Object> operation = (Map<String, Object>) item.get(method);
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        return responses.keySet();
    }
}
