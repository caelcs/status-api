package dev.status.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A service row successfully claimed by this instance for one check.
 */
public record ClaimedService(
        UUID serviceId,
        String key,
        String name,
        String env,
        String healthUrl,
        Status status,
        int consecutiveFailures,
        Instant statusChangedAt,
        Instant nextCheckAt
) {
}
