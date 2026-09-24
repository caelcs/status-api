package dev.status.adapter;

import dev.status.application.SseBroker;
import dev.status.dto.StatusEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * LISTENs on the Postgres status_events channel and forwards every transition
 * to this instance's own SSE clients (cross-instance re-broadcast).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresNotifySubscriber implements DisposableBean {

    private static final long RECONNECT_DELAY_MS = 1000;

    private final DataSource dataSource;
    private final SseBroker sseBroker;
    private final JsonMapper jsonMapper;

    private volatile boolean running = true;
    private Thread listenerThread;

    @PostConstruct
    void start() {
        this.listenerThread = Thread.ofVirtual().name("pg-notify-listener").start(this::listenLoop);
    }

    private void listenLoop() {
        while (running) {
            listenAndDrain();
        }
    }

    /** Opens one connection, LISTENs, and drains notifications until the connection drops. */
    private void listenAndDrain() {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(true);
            listen(conn);
            drain(conn.unwrap(PGConnection.class));
        } catch (Exception e) {
            if (running) {
                log.warn("notify subscriber error, reconnecting: {}", e.getMessage());
                sleepQuietly(RECONNECT_DELAY_MS);
            }
        }
    }

    private void listen(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LISTEN status_events");
        }
    }

    private void drain(PGConnection pg) throws SQLException {
        while (running) {
            PGNotification[] notifications = pg.getNotifications(1000);
            if (notifications == null || notifications.length == 0) {
                continue;
            }
            forward(notifications);
        }
    }

    private void forward(PGNotification[] notifications) {
        for (PGNotification notification : notifications) {
            handle(notification.getParameter());
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
