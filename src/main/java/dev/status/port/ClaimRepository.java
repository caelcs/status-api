package dev.status.port;

import dev.status.domain.ClaimedService;
import dev.status.domain.WriteBack;

import java.time.Duration;
import java.util.List;

/**
 * Claim-and-advance probe scheduling (ADR §4.11). Implemented against Postgres
 * native SQL so the claim is a single atomic {@code FOR UPDATE SKIP LOCKED}
 * transaction: each due row is claimed by exactly one instance per slot, and
 * its schedule is advanced at claim time.
 */
public interface ClaimRepository {

    /**
     * Atomically claims up to {@code batchSize} due services and advances their
     * schedule by {@code checkInterval} at claim time.
     */
    List<ClaimedService> claimDue(Duration checkInterval, int batchSize);

    /**
     * Persists a probe result with a plain, unconditional UPDATE (no ownership
     * re-check — the schedule was already advanced at claim time).
     */
    void writeBack(WriteBack writeBack);
}
