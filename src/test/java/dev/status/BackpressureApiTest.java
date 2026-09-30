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
 * always 0.
 */
@TestPropertySource(properties = "monitoring.max-in-flight=2")
class BackpressureApiTest extends MonitoringApiTest {

    @Autowired
    Semaphore inflightSemaphore;

    @Test
    void given_moreDueServicesThanMaxInFlight_when_probing_then_concurrencyNeverExceedsLimit_and_queueStaysEmpty() throws Exception {
        List<String> ids = new ArrayList<>();
        try (FakeService fake = new FakeService("slow")) {
            fake.setSlowDelayMs(600); // under the 800ms timeout, long enough to overlap

            for (int i = 0; i < 6; i++) {
                ids.add(register(fake.healthUrl()));
            }

            // wait until the cap is reached (proves overlap is actually exercised)
            long deadline = System.currentTimeMillis() + 5000;
            while (fake.maxActiveRequests() < 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }

            // sample the no-queue invariant several times across the busy window
            for (int i = 0; i < 5; i++) {
                assertThat(inflightSemaphore.getQueueLength())
                        .as("no submitted probe may ever park waiting for a permit (queue depth)")
                        .isZero();
                Thread.sleep(200);
            }

            assertThat(fake.maxActiveRequests())
                    .as("concurrent probes must never exceed monitoring.max-in-flight (2)")
                    .isEqualTo(2);
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
