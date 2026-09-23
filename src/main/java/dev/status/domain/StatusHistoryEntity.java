package dev.status.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_history")
public class StatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Convert(converter = StatusConverter.class)
    @Column(name = "from_status", length = 16)
    private Status fromStatus;

    @Convert(converter = StatusConverter.class)
    @Column(name = "to_status", nullable = false, length = 16)
    private Status toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "reason", length = 255)
    private String reason;

    protected StatusHistoryEntity() {
    }

    public static StatusHistoryEntity transition(UUID serviceId, Status from, Status to, String reason) {
        StatusHistoryEntity h = new StatusHistoryEntity();
        h.serviceId = serviceId;
        h.fromStatus = from;
        h.toStatus = to;
        h.reason = reason;
        return h;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public Status getFromStatus() {
        return fromStatus;
    }

    public Status getToStatus() {
        return toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getReason() {
        return reason;
    }
}
