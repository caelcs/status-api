package dev.status.adapter;

import dev.status.domain.StatusHistoryEntity;
import dev.status.port.StatusHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JpaStatusHistoryRepository extends JpaRepository<StatusHistoryEntity, Long>, StatusHistoryRepository {

    @Override
    @Query(value = """
            SELECT *
            FROM status_history h
            WHERE h.service_id = :serviceId
              AND (cast(:since as timestamptz) IS NULL OR h.changed_at >= :since)
              AND (cast(:until as timestamptz) IS NULL OR h.changed_at <= :until)
            ORDER BY h.changed_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<StatusHistoryEntity> findHistory(
            @Param("serviceId") UUID serviceId,
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("limit") int limit);
}
