package dev.status.port;

import dev.status.domain.StatusHistoryEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository {

    StatusHistoryEntity save(StatusHistoryEntity history);

    /**
     * The transitions for a service, newest first, filtered to the inclusive
     * {@code [since, until]} window and capped at {@code limit} items. Either
     * bound may be {@code null} to leave that side unbounded.
     */
    List<StatusHistoryEntity> findHistory(UUID serviceId, Instant since, Instant until, int limit);
}
