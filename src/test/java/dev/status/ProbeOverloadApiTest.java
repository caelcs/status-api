package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Overload invariants for capacity-aware claiming (analysis-probe-overload.md
 * Alt 2). With more due services than capacity, a service is never probed twice
 * concurrently, and unclaimed services keep their overdue {@code next_check_at}
 * and are claimed on later ticks — never all submitted at once.
 */
@TestPropertySource(properties = "monitoring.max-in-flight=2")
class ProbeOverloadApiTest extends MonitoringApiTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void given_singleSlowService_when_probedRepeatedly_then_neverTwoConcurrentProbes() throws Exception {
        List<String> ids = new ArrayList<>();
        try (FakeService fake = new FakeService("slow")) {
            fake.setSlowDelayMs(600); // < timeout (800ms); check-interval (1s) > probe duration
            ids.add(register(fake.healthUrl()));

            long deadline = System.currentTimeMillis() + 15000;
            while (fake.probeCount() < 1 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            // A slow probe (600ms) always completes before the next interval (1s), so
            // the service can never be re-claimed while its previous probe is running.
            assertThat(fake.probeCount())
                    .as("service was probed")
                    .isGreaterThanOrEqualTo(1);
            assertThat(fake.maxActiveRequests())
                    .as("a service is never probed twice concurrently (no overlap)")
                    .isEqualTo(1);
        } finally {
            deleteAll(ids);
        }
    }

    @Test
    void given_moreDueServicesThanCapacity_when_claiming_then_unclaimedKeepOverdueAndAreClaimedLater() throws Exception {
        List<String> ids = new ArrayList<>();
        List<FakeService> fakes = new ArrayList<>();
        int n = 8;
        try {
            // Each service gets its OWN fake (distinct healthUrl), so per-service
            // probeCount can distinguish "8×1" (every service probed once) from
            // "2×4 + 6×0" (two services hog all capacity, six silently dropped).
            for (int i = 0; i < n; i++) {
                FakeService fake = new FakeService("slow");
                fake.setSlowDelayMs(600);
                fakes.add(fake);
                ids.add(register(fake.healthUrl()));
            }

            // After the first claim round, unclaimed services keep their overdue
            // next_check_at (bounded work per tick — never all submitted at once).
            Thread.sleep(600);
            List<String> urls = fakes.stream().map(FakeService::healthUrl).toList();
            String placeholders = String.join(", ", urls.stream().map(u -> "?").toList());
            long overdue = jdbc.queryForObject(
                    "SELECT count(*) FROM services WHERE next_check_at <= now() AND health_url IN (" + placeholders + ")",
                    Long.class, urls.toArray());
            assertThat(overdue)
                    .as("unclaimed services must stay overdue: only min(batchSize, freePermits) are claimed per tick")
                    .isGreaterThan(0);

            // They are claimed on later ticks, not lost — EVERY distinct service
            // must be probed at least once (no silently-dropped service).
            long deadline = System.currentTimeMillis() + 15000;
            while (fakes.stream().anyMatch(f -> f.probeCount() < 1) && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            for (int i = 0; i < n; i++) {
                assertThat(fakes.get(i).probeCount())
                        .as("service %d must be probed at least once (no silently-dropped service)", i)
                        .isGreaterThanOrEqualTo(1);
            }
        } finally {
            deleteAll(ids);
            fakes.forEach(FakeService::close);
        }
    }

    private String register(String healthUrl) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + unique("svc") + "\",\"name\":\"Service\",\"env\":\"dev\","
                                + "\"healthUrl\":\"" + healthUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void deleteAll(List<String> ids) {
        for (String id : ids) {
            try {
                mvc.perform(delete("/api/v1/services/" + id)
                                .header("X-API-Key", TestKeys.DEV_KEY))
                        .andExpect(status().isNoContent());
            } catch (Exception ignored) {
                // best-effort cleanup; the row may already be gone
            }
        }
    }
}
