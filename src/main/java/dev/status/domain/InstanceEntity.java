package dev.status.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "instance")
public class InstanceEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected InstanceEntity() {
    }

    public static InstanceEntity start(String id, Instant startedAt) {
        InstanceEntity e = new InstanceEntity();
        e.id = id;
        e.startedAt = startedAt;
        e.lastHeartbeat = startedAt;
        return e;
    }

    public String getId() {
        return id;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
