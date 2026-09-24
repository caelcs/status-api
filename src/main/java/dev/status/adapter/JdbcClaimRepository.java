package dev.status.adapter;

import dev.status.domain.ClaimedService;
import dev.status.domain.Status;
import dev.status.domain.WriteBack;
import dev.status.port.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Claim-and-advance work queue (ADR §4.11). Each tick runs one transaction that
 * atomically claims due rows with {@code FOR UPDATE SKIP LOCKED} and advances
 * their schedule at claim time — so a duplicate probe is impossible. Write-back
 * is a plain, unconditional UPDATE (no ownership re-check).
 */
@Repository
@RequiredArgsConstructor
public class JdbcClaimRepository implements ClaimRepository {

    private static final String CLAIM_SQL = """
            SELECT id, key, name, env, health_url, status, consecutive_failures, status_changed_at
            FROM services
            WHERE next_check_at <= now()
            ORDER BY next_check_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """;

    private static final String ADVANCE_SQL = """
            UPDATE services SET next_check_at = now() + make_interval(secs => ?)
            WHERE id = ANY(?)
            """;

    private static final String WRITE_BACK_SQL = """
            UPDATE services SET
                status = ?, status_changed_at = ?, latency_ms = ?, last_checked_at = now(),
                consecutive_failures = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public List<ClaimedService> claimDue(Duration checkInterval, int batchSize) {
        List<ClaimedService> claimed = jdbc.query(CLAIM_SQL, (rs, i) -> new ClaimedService(
                        rs.getObject("id", UUID.class),
                        rs.getString("key"),
                        rs.getString("name"),
                        rs.getString("env"),
                        rs.getString("health_url"),
                        Status.fromValue(rs.getString("status")),
                        rs.getInt("consecutive_failures"),
                        toInstant(rs.getTimestamp("status_changed_at"))),
                batchSize);
        if (claimed.isEmpty()) {
            return claimed;
        }
        double intervalSeconds = checkInterval.toMillis() / 1000.0;
        String[] ids = claimed.stream().map(c -> c.serviceId().toString()).toArray(String[]::new);
        jdbc.execute((ConnectionCallback<Void>) con -> {
            try (PreparedStatement ps = con.prepareStatement(ADVANCE_SQL)) {
                ps.setDouble(1, intervalSeconds);
                ps.setArray(2, con.createArrayOf("uuid", ids));
                ps.executeUpdate();
                return null;
            }
        });
        return claimed;
    }

    @Override
    @Transactional
    public void writeBack(WriteBack wb) {
        jdbc.update(WRITE_BACK_SQL,
                wb.status().value(),
                toTimestamp(wb.statusChangedAt()),
                wb.latencyMs(),
                wb.consecutiveFailures(),
                wb.serviceId());
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Object toTimestamp(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
