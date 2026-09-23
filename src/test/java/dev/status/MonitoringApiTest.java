package dev.status;

import org.springframework.test.context.TestPropertySource;

/**
 * Base for monitoring/SSE/multi-instance tests: enables the claim loop and uses
 * short intervals so transitions happen within a second instead of 15s.
 */
@TestPropertySource(properties = {
        "monitoring.enabled=true",
        "monitoring.claim-tick-ms=400",
        "monitoring.check-interval=1s",
        "monitoring.lease-ttl=5s",
        "monitoring.timeout=2s",
        "monitoring.backoff-ms=1000,2000,4000",
        "monitoring.heartbeat-interval-ms=1000"
})
public abstract class MonitoringApiTest extends BaseApiTest {
}
