package dev.status.adapter;

import dev.status.port.InstanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;

@Repository
public class JdbcInstanceRepository implements InstanceRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO instance (id, last_heartbeat, started_at)
            VALUES (?, now(), ?)
            ON CONFLICT (id) DO UPDATE SET last_heartbeat = now()
            """;

    private static final String DELETE_STALE_SQL = "DELETE FROM instance WHERE last_heartbeat < ?";

    private final JdbcTemplate jdbc;

    public JdbcInstanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void heartbeat(String instanceId, Instant startedAt) {
        jdbc.update(UPSERT_SQL, instanceId, startedAt.atOffset(ZoneOffset.UTC));
    }

    @Override
    public int deleteStale(Instant staleBefore) {
        return jdbc.update(DELETE_STALE_SQL, staleBefore.atOffset(ZoneOffset.UTC));
    }
}
