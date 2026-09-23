package dev.status.port;

import dev.status.domain.StatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface StatusHistoryRepository {

    StatusHistoryEntity save(StatusHistoryEntity history);

    /** All transitions for a service, newest first (since/until filtering applied by the caller). */
    List<StatusHistoryEntity> findByServiceId(UUID serviceId);
}
