package dev.status.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire representation of a service's current status (api-contract §3.1).
 * Distinct from the application-service result type and the JPA entity.
 */
public record ServiceStatusResponse(
        UUID id,
        String key,
        String name,
        String env,
        String description,
        String team,
        String healthUrl,
        List<String> tags,
        String status,
        Instant statusChangedAt,
        Integer latencyMs,
        Instant lastCheckedAt,
        int consecutiveFailures
) {
}
