package dev.status.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_claim")
public class ServiceClaimEntity {

    @Id
    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "owner_instance", nullable = false, length = 255)
    private String ownerInstance;

    @Column(name = "lease_expires_at", nullable = false)
    private Instant leaseExpiresAt;

    protected ServiceClaimEntity() {
    }

    public static ServiceClaimEntity claim(UUID serviceId, String ownerInstance, Instant leaseExpiresAt) {
        ServiceClaimEntity c = new ServiceClaimEntity();
        c.serviceId = serviceId;
        c.ownerInstance = ownerInstance;
        c.leaseExpiresAt = leaseExpiresAt;
        return c;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getOwnerInstance() {
        return ownerInstance;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }
}
