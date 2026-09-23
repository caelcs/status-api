package dev.status.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Conditional write-back of a probe result. The write only takes effect if the
 * instance still owns the lease (owner_instance matches AND lease unexpired) —
 * the FR11 zombie/partition-safety re-check.
 */
public record WriteBack(
        UUID serviceId,
        String ownerInstance,
        Status status,
        Instant statusChangedAt,
        Integer latencyMs,
        int consecutiveFailures,
        Instant nextCheckAt,
        long leaseTtlMillis
) {
}
