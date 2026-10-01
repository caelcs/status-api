package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR4 / AC8: bounded in-flight backpressure — strengthened. With more due
 * services than the in-flight limit, the semaphore caps concurrent probes at
 * monitoring.max-in-flight AND the probe queue stays empty (no submitted task
 * ever parks waiting for a permit). The queue-depth assertion is what catches
 * the historical oversubscription bug: under the old "submit everything, acquire
 * inside the worker" design, the extra claims parked on the semaphore and
 * {@code getQueueLength()} grew unbounded; under the capacity-aware design it is
 * always 0. Each service is registered against its OWN fake so a per-service
 * {@code probeCount >= 1} assertion proves no service is silently dropped (a
 * shared fake could mask "2 services probed 4× + 6 probed 0×" as "all fine").
 * Aggregate concurrency is observed via the in-flight semaphore (distinct fakes
 * each see at most one concurrent probe, so a single fake's
 * {@code maxActiveRequests} can no longer observe the cap).
 */
@TestPropertySource(properties = "monitoring.max-in-flight=2")
class BackpressureApiTest extends MonitoringApiTest {

    @Autowired
    Semaphore inflightSemaphore;

    @Test
    void given_moreDueServicesThanMaxInFlight_when_probing_then_concurrencyNeverExceedsLimit_and_queueStaysEmpty() throws Exception {
        List<String> ids = new ArrayList<>();
        List<FakeService> fakes = new ArrayList<>();
        int n = 6;
        try {
            for (int i = 0; i < n; i++) {
                FakeService fake = new FakeService("slow");
                fake.setSlowDelayMs(600); // under the 800ms timeout, long enough to overlap
                fakes.add(fake);
                ids.add(register(fake.healthUrl()));
            }

            // wait until the cap is reached (proves overlap is actually exercised).
            // Distinct fakes each observe <= 1 concurrent probe, so aggregate
            // concurrency is read from the in-flight semaphore (2 - availablePermits).
            long deadline = System.currentTimeMillis() + 5000;
            int maxConcurrency = 0;
            while (System.currentTimeMillis() < deadline && maxConcurrency < 2) {
                maxConcurrency = Math.max(maxConcurrency, 2 - inflightSemaphore.availablePermits());
                Thread.sleep(50);
            }
            assertThat(maxConcurrency)
                    .as("concurrent probes must reach monitoring.max-in-flight (2)")
                    .isEqualTo(2);

            // sample the no-queue invariant several times across the busy window
            for (int i = 0; i < 5; i++) {
                assertThat(inflightSemaphore.getQueueLength())
                        .as("no submitted probe may ever park waiting for a permit (queue depth)")
                        .isZero();
                Thread.sleep(200);
            }

            // every service is eventually probed at least once (no silently-dropped service)
            long livenessDeadline = System.currentTimeMillis() + 15000;
            while (fakes.stream().anyMatch(f -> f.probeCount() < 1) && System.currentTimeMillis() < livenessDeadline) {
                Thread.sleep(100);
            }
            for (int i = 0; i < n; i++) {
                assertThat(fakes.get(i).probeCount())
                        .as("service %d must be probed at least once (no silently-dropped service)", i)
                        .isGreaterThanOrEqualTo(1);
            }
        } finally {
            // clean up the registered services so they do not pollute other
            // tests sharing the suite Postgres (e.g. ClaimLoopSqlTest)
            for (String id : ids) {
                try {
                    mvc.perform(delete("/api/v1/services/" + id)
                                    .header("X-API-Key", TestKeys.DEV_KEY))
                            .andExpect(status().isNoContent());
                } catch (Exception ignored) {
                    // best-effort cleanup; the row may already be gone
                }
            }
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
}
