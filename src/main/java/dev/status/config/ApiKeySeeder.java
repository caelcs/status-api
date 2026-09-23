package dev.status.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Provisions seed API keys on startup (FR9). Keys are supplied out-of-band via
 * config/env (docker-compose, tests), formatted "key:env:name" (comma-separated).
 * Idempotent + race-safe via INSERT ... ON CONFLICT DO NOTHING.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeySeeder implements ApplicationRunner {

    private static final String UPSERT_SQL =
            "INSERT INTO api_keys (id, key, name, env) VALUES (gen_random_uuid(), ?, ?, ?) ON CONFLICT (env, key) DO NOTHING";

    private final JdbcTemplate jdbc;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String raw = environment.getProperty("app.seed-api-keys", "");
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length < 2) {
                log.warn("ignoring malformed seed key entry: {}", trimmed);
                continue;
            }
            String key = parts[0].trim();
            String env = parts[1].trim();
            String name = parts.length >= 3 ? parts[2].trim() : key;
            jdbc.update(UPSERT_SQL, key, name, env);
        }
    }
}
