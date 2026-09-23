package dev.status.port;

import dev.status.dto.StatusEvent;

public interface NotifyPublisher {

    /** Publishes a status transition to the Postgres LISTEN/NOTIFY bus. */
    void publish(StatusEvent event);
}
