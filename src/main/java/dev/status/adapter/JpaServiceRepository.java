package dev.status.adapter;

import dev.status.domain.ServiceEntity;
import dev.status.domain.StatusSummary;
import dev.status.port.ServiceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaServiceRepository extends JpaRepository<ServiceEntity, UUID>, ServiceRepository {

    @Override
    Optional<ServiceEntity> findByKeyAndEnv(String key, String env);

    @Override
    boolean existsByKeyAndEnv(String key, String env);

    /**
     * Filtered + ordered + paginated service list. The {@code tags} column is
     * JSONB, so the tag predicate uses the Postgres {@code jsonb_exists}
     * operator (identical to {@code List.contains} — exact, case-sensitive
     * element match). {@code q} uses {@code strpos(lower(...))} to mirror the
     * case-insensitive {@code contains} semantics. Pagination honours the raw
     * {@code offset}/{@code limit} (not a page number).
     */
    @Override
    @Query(value = """
            SELECT *
            FROM services s
            WHERE s.env = :env
              AND (:status IS NULL OR s.status = :status)
              AND (:q IS NULL OR strpos(lower(s.name), lower(:q)) > 0
                   OR strpos(lower(s.key), lower(:q)) > 0)
              AND (:tag IS NULL OR jsonb_exists(s.tags, :tag))
            ORDER BY s.key
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ServiceEntity> search(
            @Param("env") String env,
            @Param("status") String status,
            @Param("q") String q,
            @Param("tag") String tag,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Whole-filtered-set counters (not the page), via a single conditional
     * aggregation over the same predicate as {@link #search}.
     */
    @Override
    default StatusSummary summarize(String env, String status, String q, String tag) {
        StatusSummaryProjection row = countByFilter(env, status, q, tag);
        return new StatusSummary(
                (int) row.getTotal(),
                (int) row.getUp(),
                (int) row.getDegraded(),
                (int) row.getDown(),
                (int) row.getUnknown());
    }

    @Query(value = """
            SELECT count(*) AS total,
                   count(*) FILTER (WHERE s.status = 'up') AS up,
                   count(*) FILTER (WHERE s.status = 'degraded') AS degraded,
                   count(*) FILTER (WHERE s.status = 'down') AS down,
                   count(*) FILTER (WHERE s.status = 'unknown') AS unknown
            FROM services s
            WHERE s.env = :env
              AND (:status IS NULL OR s.status = :status)
              AND (:q IS NULL OR strpos(lower(s.name), lower(:q)) > 0
                   OR strpos(lower(s.key), lower(:q)) > 0)
              AND (:tag IS NULL OR jsonb_exists(s.tags, :tag))
            """, nativeQuery = true)
    StatusSummaryProjection countByFilter(
            @Param("env") String env,
            @Param("status") String status,
            @Param("q") String q,
            @Param("tag") String tag);

    /** Single-row projection of the summary aggregation. */
    interface StatusSummaryProjection {
        long getTotal();

        long getUp();

        long getDegraded();

        long getDown();

        long getUnknown();
    }
}
