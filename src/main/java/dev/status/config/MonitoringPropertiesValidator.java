package dev.status.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Boot-time guard for the monitoring configuration (analysis-probe-overload.md
 * Alt 1 + Alt 2). Hard-fails on <em>impossible</em> values — refusing to start
 * with a clear, actionable message naming the property and the arithmetic —
 * while treating capacity oversubscription as a loud WARN, never a startup
 * failure: a status monitor that refuses to boot causes total observability
 * blindness, so a grown fleet or a slow service must not take monitoring down.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringPropertiesValidator {

    private final MonitoringProperties props;

    @PostConstruct
    void validate() {
        List<String> errors = new ArrayList<>();
        requirePositive("monitoring.check-interval", props.checkInterval(), errors);
        requirePositive("monitoring.timeout", props.timeout(), errors);
        requirePositive("monitoring.claim-tick-ms", props.claimTickMs(), errors);
        requirePositive("monitoring.expected-probe-latency", props.expectedProbeLatency(), errors);
        requirePositive("monitoring.max-in-flight", props.maxInFlight(), errors);
        requirePositive("monitoring.batch-size", props.batchSize(), errors);

        if (props.checkInterval().compareTo(props.timeout()) <= 0) {
            errors.add("monitoring.check-interval (" + props.checkInterval()
                    + ") must be strictly greater than monitoring.timeout (" + props.timeout()
                    + "): a probe can take up to timeout, so a shorter-or-equal interval would let a service be re-claimed while its previous probe is still running");
        }
        if (props.claimTickMs().compareTo(props.checkInterval()) >= 0) {
            errors.add("monitoring.claim-tick-ms (" + props.claimTickMs()
                    + ") must be strictly less than monitoring.check-interval (" + props.checkInterval()
                    + "): the claim tick must be finer than the check interval");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid monitoring configuration — " + String.join("; ", errors));
        }

        if (props.oversubscribed()) {
            log.warn("Monitoring capacity is oversubscribed: expectedMaxServices({}) × expectedProbeLatency({}) = {}ms exceeds maxInFlight({}) × checkInterval({}) = {}ms. "
                            + "Checks will fall behind their interval (watch status_overdue_services). This is a warning, not fatal: monitoring continues with a bounded claim rate.",
                    props.expectedMaxServices(), props.expectedProbeLatency(),
                    (long) props.expectedMaxServices() * props.expectedProbeLatency().toMillis(),
                    props.maxInFlight(), props.checkInterval(),
                    (long) props.maxInFlight() * props.checkInterval().toMillis());
        }
    }

    private static void requirePositive(String name, Duration value, List<String> errors) {
        if (value == null || value.isZero() || value.isNegative()) {
            errors.add(name + " (" + value + ") must be > 0");
        }
    }

    private static void requirePositive(String name, int value, List<String> errors) {
        if (value <= 0) {
            errors.add(name + " (" + value + ") must be > 0");
        }
    }
}
