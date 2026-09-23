package dev.status.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "instance")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstanceEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    public static InstanceEntity start(String id, Instant startedAt) {
        InstanceEntity e = new InstanceEntity();
        e.id = id;
        e.startedAt = startedAt;
        e.lastHeartbeat = startedAt;
        return e;
    }
}
