package dev.status.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunable monitoring defaults (ADR §2.4 / PRD §2.4, config-overridable).
 *
 * <p>Boot-time invariants (enforced by {@link MonitoringPropertiesValidator}):
 * <ul>
 *   <li>every value &gt; 0;</li>
 *   <li>{@code checkInterval > timeout} — a probe can take up to {@code timeout},
 *       so the interval must be longer or a service could be re-claimed while its
 *       previous probe is still running;</li>
 *   <li>{@code claimTickMs < checkInterval} — the claim tick must be finer than
 *       the check interval.</li>
 * </ul>
 *
 * <p>Capacity invariant (non-fatal — a WARN, not a startup failure): the
 * expected fleet must fit the <em>fleet-wide</em> probe capacity, i.e.
 * {@code expectedMaxServices × expectedProbeLatency ≤ maxInFlight × checkInterval × instanceCount}.
 * {@code instanceCount} is the number of {@code status-api} instances sharing the
 * Postgres work-queue, so a multi-instance deployment divides the demand across
 * them rather than comparing the global fleet against one instance's capacity.
 * {@code expectedProbeLatency} is the <em>expected average</em> probe latency and is
 * deliberately <em>not</em> validated against {@code timeout} (that would reject
 * healthy fleets whose average latency is far below the worst-case timeout).
 */
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        @DefaultValue("15s") Duration checkInterval,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("10") int maxInFlight,
        @DefaultValue("50") int batchSize,
        @DefaultValue("5000ms") Duration claimTickMs,
        @DefaultValue("50") int expectedMaxServices,
        @DefaultValue("1s") Duration expectedProbeLatency,
        @DefaultValue("1") int instanceCount
) {

    /**
     * True when the expected fleet exceeds the fleet-wide probe capacity:
     * {@code expectedMaxServices × expectedProbeLatency > maxInFlight × checkInterval × instanceCount}.
     * Long arithmetic throughout so no intermediate value truncates.
     */
    public boolean oversubscribed() {
        long demand = (long) expectedMaxServices * expectedProbeLatency.toMillis();
        long capacity = (long) maxInFlight * checkInterval.toMillis() * instanceCount;
        return demand > capacity;
    }
}
