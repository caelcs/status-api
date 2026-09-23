package dev.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ServiceReadApiTest extends BaseApiTest {

    @Test
    void given_devAndProdServices_when_listByEnv_then_onlyMatchingEnv() throws Exception {
        String tag = unique("envscope");
        register(unique("payments"), "dev", TestKeys.DEV_KEY, tag);
        register(unique("auth"), "dev", TestKeys.DEV_KEY, tag);
        register(unique("payments"), "prod", TestKeys.PROD_KEY, tag);

        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.summary.total").value(2));

        mvc.perform(get("/api/v1/services").param("env", "prod").param("tag", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    @Test
    void given_services_when_filterByStatus_then_onlyMatching() throws Exception {
        String tag = unique("status");
        register(unique("payments"), "dev", TestKeys.DEV_KEY, tag);
        register(unique("auth"), "dev", TestKeys.DEV_KEY, tag);

        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag).param("status", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag).param("status", "up"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void given_invalidStatusFilter_when_list_then_400() throws Exception {
        mvc.perform(get("/api/v1/services").param("env", "dev").param("status", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void given_missingEnv_when_list_then_400() throws Exception {
        mvc.perform(get("/api/v1/services"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void given_services_when_filterByQ_then_nameOrKeyMatch() throws Exception {
        String tag = unique("q");
        String key = unique("payments");
        register(key, "dev", TestKeys.DEV_KEY, tag);
        register(unique("auth"), "dev", TestKeys.DEV_KEY, tag);

        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag).param("q", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].key").value(key));
    }

    @Test
    void given_servicesWithTags_when_filterByTag_then_onlyMatching() throws Exception {
        String tag = unique("tagfilter");
        String key = unique("payments");
        register(key, "dev", TestKeys.DEV_KEY, tag);
        registerNoTag(unique("auth"), "dev", TestKeys.DEV_KEY);

        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].key").value(key));
    }

    @Test
    void given_manyServices_when_paginate_then_limitOffset() throws Exception {
        String tag = unique("page");
        for (int i = 0; i < 5; i++) {
            register(unique("svc") + "-" + i, "dev", TestKeys.DEV_KEY, tag);
        }
        mvc.perform(get("/api/v1/services").param("env", "dev").param("tag", tag)
                        .param("limit", "2").param("offset", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.offset").value(1))
                // summary reflects the full filtered set, not the page
                .andExpect(jsonPath("$.summary.total").value(5));
    }

    @Test
    void given_unknownId_when_getService_then_404() throws Exception {
        mvc.perform(get("/api/v1/services/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private void register(String key, String env, String apiKey, String tag) throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + key + "\",\"name\":\"Service " + key + "\",\"env\":\"" + env
                                + "\",\"healthUrl\":\"http://" + key + ":8080/health\",\"tags\":[\"" + tag + "\"]}"))
                .andExpect(status().isCreated());
    }

    private void registerNoTag(String key, String env, String apiKey) throws Exception {
        mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(key, "Service " + key, env, "http://" + key + ":8080/health")))
                .andExpect(status().isCreated());
    }
}
