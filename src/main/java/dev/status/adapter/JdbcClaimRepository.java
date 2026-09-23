package dev.status.adapter;

import dev.status.domain.ClaimedService;
import dev.status.domain.Status;
import dev.status.domain.WriteBack;
import dev.status.port.ClaimRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcClaimRepository implements ClaimRepository {

    private static final String CLAIM_SQL = """
            WITH due AS (
                SELECT s.id, s.key, s.name, s.env, s.health_url, s.status,
                       s.consecutive_failures, s.status_changed_at, s.next_check_at
                FROM services s
                LEFT JOIN service_claim sc ON sc.service_id = s.id
                WHERE s.next_check_at <= now()
                  AND (sc.owner_instance IS NULL OR sc.lease_expires_at < now() OR sc.owner_instance = ?)
                ORDER BY s.next_check_at ASC
                LIMIT ?
            ), claimed AS (
                INSERT INTO service_claim (service_id, owner_instance, lease_expires_at)
                SELECT d.id, ?, now() + make_interval(secs => ?)
                FROM due d
                ON CONFLICT (service_id) DO UPDATE
                    SET owner_instance = ?, lease_expires_at = now() + make_interval(secs => ?)
                    WHERE service_claim.owner_instance = ? OR service_claim.lease_expires_at < now()
                RETURNING service_id
            )
            SELECT d.id, d.key, d.name, d.env, d.health_url, d.status,
                   d.consecutive_failures, d.status_changed_at, d.next_check_at
            FROM due d
            JOIN claimed c ON c.service_id = d.id
            ORDER BY d.next_check_at ASC
            """;

    private static final String WRITE_BACK_SQL = """
            UPDATE services SET
                status = ?, status_changed_at = ?, latency_ms = ?, last_checked_at = now(),
                consecutive_failures = ?, next_check_at = ?
            WHERE id = ?
              AND EXISTS (
                  SELECT 1 FROM service_claim sc
                  WHERE sc.service_id = services.id
                    AND sc.owner_instance = ?
                    AND sc.lease_expires_at >= now()
              )
            """;

    private static final String RENEW_SQL = """
            UPDATE service_claim SET lease_expires_at = now() + make_interval(secs => ?)
            WHERE service_id = ? AND owner_instance = ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcClaimRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public List<ClaimedService> claimDue(String ownerInstance, long leaseTtlMillis, int cap) {
        double ttlSeconds = leaseTtlMillis / 1000.0;
        return jdbc.query(CLAIM_SQL, (rs, i) -> new ClaimedService(
                        rs.getObject("id", UUID.class),
                        rs.getString("key"),
                        rs.getString("name"),
                        rs.getString("env"),
                        rs.getString("health_url"),
                        Status.fromValue(rs.getString("status")),
                        rs.getInt("consecutive_failures"),
                        toInstant(rs.getTimestamp("status_changed_at")),
                        toInstant(rs.getTimestamp("next_check_at"))),
                ownerInstance, cap, ownerInstance, ttlSeconds, ownerInstance, ttlSeconds, ownerInstance);
    }

    @Override
    @Transactional
    public boolean writeBack(WriteBack wb) {
        double ttlSeconds = wb.leaseTtlMillis() / 1000.0;
        int updated = jdbc.update(WRITE_BACK_SQL,
                wb.status().value(),
                toTimestamp(wb.statusChangedAt()),
                wb.latencyMs(),
                wb.consecutiveFailures(),
                toTimestamp(wb.nextCheckAt()),
                wb.serviceId(),
                wb.ownerInstance());
        if (updated == 0) {
            return false;
        }
        jdbc.update(RENEW_SQL, ttlSeconds, wb.serviceId(), wb.ownerInstance());
        return true;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Object toTimestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
