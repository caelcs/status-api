package dev.status;

import com.jayway.jsonpath.JsonPath;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR3 / FR11 / AC7 / AC14: with 3 instances, killing the owner causes survivors
 * to re-claim within the lease TTL + one tick, with monitoring continuity and
 * no duplicate probing.
 */
class MultiInstanceRebalanceTest {

    @Test
    void given_ownerKilled_when_leaseExpires_then_survivorsReclaim_withoutDuplicates() throws Exception {
        List<AppInstance> instances = new ArrayList<>();
        try (FakeService fake = new FakeService("up")) {
            // boot instance 0 first so it becomes the sole owner
            AppInstance owner = AppInstance.boot(AppInstance.baseProps(2));
            instances.add(owner);

            HttpResponse<String> reg = owner.post("/api/v1/services",
                    registration(unique("svc"), "Service", "dev", fake.healthUrl()), TestKeys.DEV_KEY);
            String id = JsonPath.read(reg.body(), "$.id");
            awaitStatus(owner, id, "up", 15);

            // two survivors join while owner already holds the lease
            instances.add(AppInstance.boot(AppInstance.baseProps(2)));
            instances.add(AppInstance.boot(AppInstance.baseProps(2)));

            int probesBefore = fake.probeCount();

            // kill the owner
            owner.close();

            // wait lease TTL (2s) + one tick + buffer
            Thread.sleep(5000);

            int probesAfter = fake.probeCount();
            assertThat(probesAfter)
                    .as("survivors must re-claim and keep probing (continuity)")
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
