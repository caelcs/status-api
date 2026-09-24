package dev.status.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A service row claimed by this instance for one check. Its schedule was
 * already advanced at claim time, so only the snapshot fields the prober needs
 * are carried forward.
 */
public record ClaimedService(
        UUID serviceId,
        String key,
        String name,
        String env,
        String healthUrl,
        Status status,
        int consecutiveFailures,
        Instant statusChangedAt
) {
}
