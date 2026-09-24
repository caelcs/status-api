package dev.status.port;

import dev.status.domain.ServiceEntity;
import dev.status.domain.StatusSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository {

    ServiceEntity save(ServiceEntity service);

    Optional<ServiceEntity> findById(UUID id);

    Optional<ServiceEntity> findByKeyAndEnv(String key, String env);

    boolean existsByKeyAndEnv(String key, String env);

    void delete(ServiceEntity service);

    /**
     * The filtered + ordered + paginated service list. {@code status} is the
     * canonical lowercase wire value ({@code "up"|"degraded"|"down"|"unknown"})
     * or {@code null} for no filter; {@code q} is a case-insensitive substring
     * on {@code name} or {@code key} (or {@code null}); {@code tag} is an exact
     * element match on the {@code tags} JSONB array (or {@code null}). Rows are
     * ordered by {@code key} ascending and paginated by a raw {@code offset}
     * (not a page number) and {@code limit}.
     */
    List<ServiceEntity> search(String env, String status, String q, String tag, int limit, int offset);

    /**
     * The summary counters over the whole filtered set (not the returned page),
     * for the same filter predicate as {@link #search}.
     */
    StatusSummary summarize(String env, String status, String q, String tag);
}
