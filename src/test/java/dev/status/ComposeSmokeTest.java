package dev.status;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC12 / FR9: validates the docker-compose topology (postgres + 3 instances +
 * 5 self-registering mock services) and the injected env vars.
 */
class ComposeSmokeTest {

    @Test
    void given_composeFile_when_inspected_then_fullTopologyPresent() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        for (String svc : List.of(
                "postgres",
                "status-api-1", "status-api-2", "status-api-3",
                "mock-auth", "mock-payments", "mock-notifications", "mock-search", "mock-ai")) {
            assertThat(compose).as("compose must define service '%s'", svc).contains(svc + ":");
        }

        // datasource + seed keys + mock API keys injected via environment
        assertThat(compose).contains("SPRING_DATASOURCE_URL");
        assertThat(compose).contains("APP_SEED_API_KEYS");
        assertThat(compose).contains("MOCK_API_KEY");
        assertThat(compose).contains("STATUS_API_URL");
    }
}
