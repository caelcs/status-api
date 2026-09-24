package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR11 / AC7 / AC14: with 3 instances, recycling one instance causes no
 * duplicate probing (the schedule advanced at claim time), the next check
 * happens on schedule, state is preserved, and rebalance is immediate (no
 * TTL to wait out).
 */
class MultiInstanceRebalanceTest {

    @Test
    void given_ownerKilled_when_recycled_then_survivorsContinue_withoutDuplicates() throws Exception {
        List<AppInstance> instances = new ArrayList<>();
        try (FakeService fake = new FakeService("up")) {
            // boot instance 0 first so it becomes the first claimer
            AppInstance owner = AppInstance.boot(AppInstance.baseProps());
            instances.add(owner);

            HttpResponse<String> reg = owner.post("/api/v1/services",
                    registration(unique("svc"), "Service", "dev", fake.healthUrl()), TestKeys.DEV_KEY);
            String id = JsonPath.read(reg.body(), "$.id");
            awaitStatus(owner, id, "up", 15);

            // two survivors join while the owner is still running
            instances.add(AppInstance.boot(AppInstance.baseProps()));
            instances.add(AppInstance.boot(AppInstance.baseProps()));

            int probesBefore = fake.probeCount();

            // recycle the owner
            owner.close();

            // wait one check interval + one claim tick + buffer (no TTL)
            Thread.sleep(3000);

            int probesAfter = fake.probeCount();
            assertThat(probesAfter)
                    .as("survivors must keep probing (continuity)")
                    .isGreaterThan(probesBefore);
            // no duplicate probing: the probe rate stays ~1/check-interval, not 2x/3x
            assertThat(probesAfter - probesBefore)
                    .as("no duplicate probing across survivors")
                    .isLessThanOrEqualTo(10);

            // state preserved across failover
            HttpResponse<String> status = instances.get(1).get("/api/v1/services/" + id);
            assertThat(status.statusCode()).isEqualTo(200);
            String currentStatus = JsonPath.read(status.body(), "$.status");
            assertThat(currentStatus).isEqualTo("up");
            int failures = JsonPath.read(status.body(), "$.consecutiveFailures");
            assertThat(failures).isZero();
        } finally {
            instances.forEach(AppInstance::close);
        }
    }

    @Test
    void given_multipleInstances_when_sameDueWindow_then_eachSlotProbedOnce() throws Exception {
        int instanceCount = 3;
        int serviceCount = 6;
        List<AppInstance> instances = new ArrayList<>();
        try (FakeService fake = new FakeService("up")) {
            for (int i = 0; i < instanceCount; i++) {
                Map<String, Object> props = AppInstance.baseProps();
                props.put("monitoring.check-interval", "3s"); // wide window: exactly one claim round
                instances.add(AppInstance.boot(props));
            }

            // all due now, pointing at the same fake service
            for (int i = 0; i < serviceCount; i++) {
                HttpResponse<String> reg = instances.get(0).post("/api/v1/services",
                        registration(unique("svc"), "Service", "dev", fake.healthUrl()), TestKeys.DEV_KEY);
                assertThat(reg.statusCode()).isEqualTo(201);
            }

            // wait for the single claim round to complete (each slot probed exactly once)
            long deadline = System.currentTimeMillis() + 8000;
            while (fake.probeCount() < serviceCount && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            assertThat(fake.probeCount())
                    .as("pod count must not multiply the check rate: each due slot is probed exactly once")
                    .isEqualTo(serviceCount);
        } finally {
            instances.forEach(AppInstance::close);
        }
    }

    private static String registration(String key, String name, String env, String healthUrl) {
        return "{\"key\":\"" + key + "\",\"name\":\"" + name + "\",\"env\":\"" + env
                + "\",\"healthUrl\":\"" + healthUrl + "\"}";
    }

    private static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private static void awaitStatus(AppInstance instance, String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> r = instance.get("/api/v1/services/" + id);
            if (r.statusCode() == 200) {
                String current = JsonPath.read(r.body(), "$.status");
                if (expected.equals(current)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("service did not reach status " + expected);
    }
}
