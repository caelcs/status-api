package dev.status.port;

import java.time.Instant;

public interface InstanceRepository {

    /** Upserts this instance's heartbeat row. */
    void heartbeat(String instanceId, Instant startedAt);

    /** Deletes membership rows whose heartbeat is stale (dead instances). */
    int deleteStale(Instant staleBefore);
}
