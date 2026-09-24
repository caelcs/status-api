package dev.status.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Unconditional write-back of a probe result (ADR §4.11). The schedule was
 * already advanced at claim time, so there is no ownership re-check to
 * perform.
 */
public record WriteBack(
        UUID serviceId,
        Status status,
        Instant statusChangedAt,
        Integer latencyMs,
        int consecutiveFailures
) {
}
