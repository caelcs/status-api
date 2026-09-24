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
        "monitoring.timeout=2s"
})
public abstract class MonitoringApiTest extends BaseApiTest {
}
