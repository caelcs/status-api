package dev.status.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Non-fatal capacity signal (analysis-probe-overload.md Alt 1). Always reports
 * {@code UP}; oversubscription is surfaced through the {@code overloaded} detail
 * so {@code /actuator/health/readiness} is never taken DOWN by a slow fleet or a
 * grown service set. The bean name ({@code probeCapacity}) is what
 * {@code application.yml} excludes from the {@code readiness} group and includes
 * in the dedicated {@code capacity} group.
 */
@Component("probeCapacity")
@RequiredArgsConstructor
public class ProbeCapacityHealthIndicator implements HealthIndicator {

    private final MonitoringProperties props;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("overloaded", props.oversubscribed())
                .withDetail("expectedMaxServices", props.expectedMaxServices())
                .withDetail("expectedProbeLatency", props.expectedProbeLatency().toString())
                .withDetail("maxInFlight", props.maxInFlight())
                .withDetail("checkInterval", props.checkInterval().toString())
                .build();
    }
}
