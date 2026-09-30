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
 * expected fleet must fit the per-instance probe capacity, i.e.
 * {@code expectedMaxServices × expectedProbeLatency ≤ maxInFlight × checkInterval}.
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
        @DefaultValue("1s") Duration expectedProbeLatency
) {

    /**
     * True when the expected fleet exceeds the per-instance probe capacity:
     * {@code expectedMaxServices × expectedProbeLatency > maxInFlight × checkInterval}.
     */
    public boolean oversubscribed() {
        long demand = (long) expectedMaxServices * expectedProbeLatency.toMillis();
        long capacity = (long) maxInFlight * checkInterval.toMillis();
        return demand > capacity;
    }
}
