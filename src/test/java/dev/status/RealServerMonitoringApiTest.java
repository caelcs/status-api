package dev.status;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Base for real-HTTP monitoring tests (SSE, fault injection): boots the app on
 * a random port with monitoring enabled and short intervals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "monitoring.enabled=true",
        "monitoring.claim-tick-ms=400",
        "monitoring.check-interval=1s",
        "monitoring.lease-ttl=5s",
        "monitoring.timeout=2s",
        "monitoring.backoff-ms=1000,2000,4000",
        "monitoring.heartbeat-interval-ms=1000"
})
public abstract class RealServerMonitoringApiTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresHolder.postgres::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresHolder.postgres::getUsername);
        registry.add("spring.datasource.password", PostgresHolder.postgres::getPassword);
    }

    @LocalServerPort
    protected int port;

    protected final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> post(String path, String body, String apiKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            b.header("X-API-Key", apiKey);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected static String registration(String key, String name, String env, String healthUrl) {
        return "{\"key\":\"" + key + "\",\"name\":\"" + name + "\",\"env\":\"" + env
                + "\",\"healthUrl\":\"" + healthUrl + "\"}";
    }

    protected static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
