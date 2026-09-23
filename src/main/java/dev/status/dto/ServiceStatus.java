package dev.status.dto;

import dev.status.domain.ServiceEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire representation of a service's current status (api-contract §3.1).
 */
public record ServiceStatus(
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

    public static ServiceStatus from(ServiceEntity e) {
        return new ServiceStatus(
                e.getId(),
                e.getKey(),
                e.getName(),
                e.getEnv(),
                e.getDescription(),
                e.getTeam(),
                e.getHealthUrl(),
                e.getTags(),
                e.getStatus().value(),
                e.getStatusChangedAt(),
                e.getLatencyMs(),
                e.getLastCheckedAt(),
                e.getConsecutiveFailures());
    }
}
