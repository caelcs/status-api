package dev.status.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunable monitoring defaults (ADR §2.4 / PRD §2.4, config-overridable).
 * Claim-and-advance needs only the fixed interval, the probe timeout, the
 * bounded in-flight limit, and the per-tick batch size.
 */
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        @DefaultValue("15s") Duration checkInterval,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("10") int maxInFlight,
        @DefaultValue("50") int batchSize
) {
}
