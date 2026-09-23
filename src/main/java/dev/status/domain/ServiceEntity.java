package dev.status.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key", nullable = false, length = 64)
    private String key;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "env", nullable = false, length = 64)
    private String env;

    @Column(name = "description")
    private String description;

    @Column(name = "team")
    private String team;

    @Column(name = "health_url", nullable = false, length = 2048)
    private String healthUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags")
    private List<String> tags = new ArrayList<>();

    @Convert(converter = StatusConverter.class)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.UNKNOWN;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "next_check_at", nullable = false)
    private Instant nextCheckAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ServiceEntity create(String key, String name, String env, String healthUrl) {
        ServiceEntity e = new ServiceEntity();
        e.key = key;
        e.name = name;
        e.env = env;
        e.healthUrl = healthUrl;
        e.status = Status.UNKNOWN;
        e.consecutiveFailures = 0;
        e.nextCheckAt = Instant.now();
        return e;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (nextCheckAt == null) {
            nextCheckAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Null-safe setter: a missing tags array becomes an empty list (wire: []). */
    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}
