package dev.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared API-first test harness: a real Testcontainers Postgres + MockMvc.
 * Arrange and assert happen through the HTTP API only (no repository/SQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseApiTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresHolder.postgres::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresHolder.postgres::getUsername);
        registry.add("spring.datasource.password", PostgresHolder.postgres::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    protected static String registration(String key, String name, String env, String healthUrl) {
        return "{\"key\":\"" + key + "\",\"name\":\"" + name + "\",\"env\":\"" + env
                + "\",\"healthUrl\":\"" + healthUrl + "\"}";
    }

    /** Unique suffix to isolate test data (arrange via HTTP only, no DB reset). */
    protected static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
