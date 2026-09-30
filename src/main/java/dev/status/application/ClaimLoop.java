package dev.status.application;

import dev.status.config.MonitoringProperties;
import dev.status.domain.ClaimedService;
import dev.status.port.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Claim-and-advance monitoring loop (ADR §4.11). Every tick atomically claims
 * at most {@code min(batchSize, freePermits)} due services ({@code FOR UPDATE
 * SKIP LOCKED}), acquires one in-flight permit per claimed row <em>before</em>
 * submitting it, and dispatches each to the {@link ServiceProbeWorker}. Because
 * a permit is held before submit, a claimed task always starts immediately and
 * never parks — live tasks are bounded by {@code maxInFlight} by construction
 * (the queue is impossible, not merely capped). {@code fixedDelay} serializes
 * ticks (one at a time), which is what makes the free-permit read race-free; it
 * does not, by itself, bound probe overlap — that guarantee comes from the
 * permit-before-submit ordering plus the {@code checkInterval > timeout} boot
 * invariant ({@code MonitoringProperties}).
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ClaimLoop {

    private final MonitoringProperties props;
    private final ClaimRepository claimRepository;
    private final MonitoringMetrics metrics;
    private final ExecutorService probeExecutor;
    private final ServiceProbeWorker worker;
    private final Semaphore inflight;

    /**
     * The claim tick is a typed, validated {@link MonitoringProperties#claimTickMs()}
     * field (bound to {@code monitoring.claim-tick-ms}, so {@code MONITORING_CLAIM_TICK_MS}
     * still works). {@code @Scheduled} reads the same key.
     */
    @Scheduled(fixedDelayString = "${monitoring.claim-tick-ms:5000}")
    public void claimAndProbe() {
        // Ticks are serialized by fixedDelay, so availablePermits() is read only by
        // this single scheduled thread. Workers only RELEASE permits (never acquire —
        // acquisition happens here, before submit), so free permits are monotonically
        // non-decreasing across a tick and cannot be raced by a concurrent tick.
        int freePermits = inflight.availablePermits();
        if (freePermits == 0) {
            log.debug("claim tick skipped: semaphore saturated ({} in flight)", props.maxInFlight());
            metrics.recordSkippedTick();
            return;
        }
        int batch = Math.min(props.batchSize(), freePermits);
        List<ClaimedService> claimed;
        try {
            claimed = claimRepository.claimDue(props.checkInterval(), batch);
        } catch (Exception e) {
            log.warn("claim loop error: {}", e.getMessage());
            return;
        }
        metrics.recordClaim(claimed.size());
        for (ClaimedService service : claimed) {
            // Cannot block: claimed.size() <= freePermits was read this tick, and no
            // other thread acquires (workers only release). The submitted task releases
            // this permit in its finally block.
            inflight.acquireUninterruptibly();
            metrics.setInflight(inflightCount());
            probeExecutor.submit(() -> worker.probe(service));
        }
    }

    private int inflightCount() {
        return props.maxInFlight() - inflight.availablePermits();
    }
}
