package dev.status.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * SSE payload for a status transition (api-contract §3.4).
 */
public record StatusEvent(
        String event,
        ServiceRef service,
        String from,
        String to,
        Instant at,
        Integer latencyMs,
        String reason
) {

    public record ServiceRef(UUID id, String key, String name, String env) {
    }

    public static StatusEvent changed(ServiceRef service, String from, String to, Instant at,
                                      Integer latencyMs, String reason) {
        return new StatusEvent("status.changed", service, from, to, at, latencyMs, reason);
    }
}
