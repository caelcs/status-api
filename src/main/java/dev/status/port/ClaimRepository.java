package dev.status.port;

import dev.status.domain.ClaimedService;
import dev.status.domain.WriteBack;

import java.util.List;

/**
 * Distributed claim/lease operations. Implemented against Postgres native SQL
 * so the claim UPDATE is a single atomic conditional statement (exactly one
 * winner per due row).
 */
public interface ClaimRepository {

    /**
     * Atomically claims up to {@code cap} due, unowned-or-expired services.
     */
    List<ClaimedService> claimDue(String ownerInstance, long leaseTtlMillis, int cap);

    /**
     * Persists a probe result + next trigger, re-verifying ownership. Returns
     * {@code false} if ownership was lost (the result is discarded).
     */
    boolean writeBack(WriteBack writeBack);
}
