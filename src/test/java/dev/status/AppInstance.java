package dev.status;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * A booted app instance (a Spring context on a random port) sharing the suite
 * Postgres. Used by the cross-instance and multi-instance tests.
 */
public final class AppInstance implements AutoCloseable {

    public static Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", "0");
        props.put("spring.datasource.url", PostgresHolder.postgres.getJdbcUrl());
        props.put("spring.datasource.username", PostgresHolder.postgres.getUsername());
        props.put("spring.datasource.password", PostgresHolder.postgres.getPassword());
        props.put("app.seed-api-keys", "dev-key:dev:dev-client,prod-key:prod:prod-client");
        props.put("monitoring.enabled", "true");
        props.put("monitoring.claim-tick-ms", "400");
        props.put("monitoring.check-interval", "1s");
        props.put("monitoring.timeout", "2s");
        props.put("spring.main.banner-mode", "off");
        return props;
    }

    private final ConfigurableApplicationContext context;
    public final int port;
    public final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private AppInstance(ConfigurableApplicationContext context, int port) {
        this.context = context;
        this.port = port;
    }

    public static AppInstance boot(Map<String, Object> props) {
        SpringApplication app = new SpringApplication(StatusApiApplication.class);
        // command-line args have the highest precedence, so they override the
        // application.yml datasource/env defaults (e.g. localhost:5432)
        String[] args = props.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext ctx = app.run(args);
        int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
        return new AppInstance(ctx, port);
    }

    public String url(String path) {
        return "http://localhost:" + port + path;
    }

    public HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String path, String body, String apiKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null) {
            b.header("X-API-Key", apiKey);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Override
    public void close() {
        context.close();
    }
}
