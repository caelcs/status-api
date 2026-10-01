package dev.status.config;

import dev.status.port.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Non-fatal capacity signal (analysis-probe-overload.md Alt 1). Always reports
 * {@code UP}; overload is surfaced through the details so
 * {@code /actuator/health/readiness} is never taken DOWN by a slow fleet or a
 * grown service set. The bean name ({@code probeCapacity}) is what
 * {@code application.yml} excludes from the {@code readiness} group and includes
 * in the dedicated {@code capacity} group.
 *
 * <p>The {@code overloaded} detail is a <em>runtime</em> signal, not a static
 * config ratio: it is {@code overdueServices > 0}, i.e. a service actually
 * falling behind its schedule. {@code configuredOversubscribed} is the config
 * lint (capacity arithmetic) and is now secondary. {@code overdueServices} is
 * {@code -1} when the database is unreachable (see {@link #overdueCount()}).
 */
@Component("probeCapacity")
@RequiredArgsConstructor
public class ProbeCapacityHealthIndicator implements HealthIndicator {

    private final MonitoringProperties props;
    private final ClaimRepository claimRepository;

    @Override
    public Health health() {
        long overdue = overdueCount();
        boolean overloaded = overdue > 0;
        return Health.up()
                .withDetail("overloaded", overloaded)
                .withDetail("overdueServices", overdue)
                .withDetail("configuredOversubscribed", props.oversubscribed())
                .withDetail("expectedMaxServices", props.expectedMaxServices())
                .withDetail("expectedProbeLatency", props.expectedProbeLatency().toString())
                .withDetail("maxInFlight", props.maxInFlight())
                .withDetail("checkInterval", props.checkInterval().toString())
                .build();
    }

    /**
     * {@code -1} means "unknown (database unavailable)". A DB outage must not
     * throw out of the health endpoint, and must not masquerade as healthy (0).
     */
    private long overdueCount() {
        try {
            return claimRepository.overdueCount();
        } catch (Exception e) {
            return -1L;
        }
    }
}
