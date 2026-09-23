package dev.status.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transition history response (api-contract §3.5). Distinct from the
 * application-service result type.
 */
public record StatusHistoryResponse(
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
