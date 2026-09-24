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

/**
 * Claim-and-advance monitoring loop (ADR §4.11). Every tick atomically claims
 * due services ({@code FOR UPDATE SKIP LOCKED}) and dispatches each to the
 * {@link ServiceProbeWorker} for probing under the bounded in-flight semaphore.
 * {@code fixedDelay} serializes ticks, so no overlap.
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

    @Scheduled(fixedDelayString = "${monitoring.claim-tick-ms:5000}")
    public void claimAndProbe() {
        List<ClaimedService> claimed;
        try {
            claimed = claimRepository.claimDue(props.checkInterval(), props.batchSize());
        } catch (Exception e) {
            log.warn("claim loop error: {}", e.getMessage());
            return;
        }
        metrics.recordClaim(claimed.size());
        claimed.forEach(service -> probeExecutor.submit(() -> worker.probe(service)));
    }
}
