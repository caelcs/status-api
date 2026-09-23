package dev.status.adapter;

import dev.status.application.SseBroker;
import dev.status.dto.StatusEvent;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * LISTENs on the Postgres status_events channel and forwards every transition
 * to this instance's own SSE clients (cross-instance re-broadcast).
 */
@Component
public class PostgresNotifySubscriber implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotifySubscriber.class);
    private static final long RECONNECT_DELAY_MS = 1000;

    private final DataSource dataSource;
    private final SseBroker sseBroker;
    private final JsonMapper jsonMapper;

    private volatile boolean running = true;
    private Thread listenerThread;

    public PostgresNotifySubscriber(DataSource dataSource, SseBroker sseBroker, JsonMapper jsonMapper) {
        this.dataSource = dataSource;
        this.sseBroker = sseBroker;
        this.jsonMapper = jsonMapper;
        this.listenerThread = Thread.ofVirtual().name("pg-notify-listener").start(this::listenLoop);
    }

    private void listenLoop() {
        while (running) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("LISTEN status_events");
                }
                PGConnection pg = conn.unwrap(PGConnection.class);
                while (running) {
                    PGNotification[] notifications = pg.getNotifications(1000);
                    if (notifications != null) {
                        for (PGNotification n : notifications) {
                            handle(n.getParameter());
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.warn("notify subscriber error, reconnecting: {}", e.getMessage());
                    sleepQuietly(RECONNECT_DELAY_MS);
                }
            }
        }
    }

    private void handle(String payload) {
        try {
            StatusEvent event = jsonMapper.readValue(payload, StatusEvent.class);
            sseBroker.broadcast(event);
        } catch (Exception e) {
            log.warn("failed to parse notify payload: {}", e.getMessage());
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
    }
}
