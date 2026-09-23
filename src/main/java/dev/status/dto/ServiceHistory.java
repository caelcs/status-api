package dev.status.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transition history for a service — the application-service result type
 * (mapped to the wire by {@code dev.status.web.ServiceResponseMapper}).
 * Wire shape is api-contract §3.5.
 */
public record ServiceHistory(
        List<StatusHistoryItem> items,
        Instant since,
        Instant until
) {

    public record StatusHistoryItem(
            UUID serviceId,
            String from,
            String to,
            Instant at,
            String reason
    ) {
    }
}
