package dev.status.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Tunable monitoring defaults (ADR §2.4 / PRD §2.4, config-overridable).
 */
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(
        @DefaultValue("15s") Duration checkInterval,
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("30s") Duration leaseTtl,
        @DefaultValue("50") int claimCap,
        @DefaultValue("10") int maxInFlight,
        @DefaultValue({"15000", "30000", "60000"}) List<Long> backoffMs,
        @DefaultValue("0.2") double jitter,
        @DefaultValue("5s") Duration claimTick,
        @DefaultValue("10s") Duration heartbeatInterval,
        @DefaultValue("30s") Duration heartbeatTtl
) {
}
