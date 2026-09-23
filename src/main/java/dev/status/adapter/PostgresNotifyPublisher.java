package dev.status.adapter;

import dev.status.dto.StatusEvent;
import dev.status.port.NotifyPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes a transition on the Postgres NOTIFY bus (channel status_events).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresNotifyPublisher implements NotifyPublisher {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    @Override
    public void publish(StatusEvent event) {
        try {
            String json = jsonMapper.writeValueAsString(event);
            jdbc.execute("SELECT pg_notify('status_events', ?)", (PreparedStatementCallback<Void>) ps -> {
                ps.setString(1, json);
                ps.execute();
                return null;
            });
        } catch (Exception e) {
            log.warn("failed to publish status event: {}", e.getMessage());
        }
    }
}
