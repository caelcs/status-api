package dev.status.application;

import dev.status.config.MonitoringProperties;
import dev.status.domain.ClaimedService;
import dev.status.domain.ProbeResult;
import dev.status.domain.Status;
import dev.status.domain.StatusHistoryEntity;
import dev.status.domain.WriteBack;
import dev.status.dto.StatusEvent;
import dev.status.port.ClaimRepository;
import dev.status.port.HealthProbeClient;
import dev.status.port.NotifyPublisher;
import dev.status.port.StatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distributed claim/lease monitoring loop (ADR §4.4). Every tick atomically
 * claims due services (single conditional UPDATE -> one winner), probes them
 * under a bounded in-flight semaphore, and writes back the next trigger with a
 * conditional ownership re-check.
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class ClaimLoop {

    private static final Logger log = LoggerFactory.getLogger(ClaimLoop.class);

    private final MonitoringProperties props;
    private final ClaimRepository claimRepository;
    private final StatusHistoryRepository historyRepository;
    private final HealthProbeClient probeClient;
    private final ProbeStatusMapping mapping;
    private final BackoffCalculator backoff;
    private final SseBroker sseBroker;
    private final NotifyPublisher notifyPublisher;
    private final MonitoringMetrics metrics;
    private final String instanceId;

    private final Semaphore inflight;
    private final ExecutorService probeExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Lock tickLock = new ReentrantLock();

    public ClaimLoop(MonitoringProperties props,
                     ClaimRepository claimRepository,
                     StatusHistoryRepository historyRepository,
                     HealthProbeClient probeClient,
                     ProbeStatusMapping mapping,
                     SseBroker sseBroker,
                     NotifyPublisher notifyPublisher,
                     MonitoringMetrics metrics,
                     InstanceIdentity identity) {
        this.props = props;
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.probeClient = probeClient;
        this.mapping = mapping;
        this.backoff = new BackoffCalculator(props.backoffMs(), props.jitter());
        this.sseBroker = sseBroker;
        this.notifyPublisher = notifyPublisher;
        this.metrics = metrics;
        this.instanceId = identity.id();
        this.inflight = new Semaphore(props.maxInFlight());
    }

    @Scheduled(fixedDelayString = "${monitoring.claim-tick-ms:5000}")
    public void claimAndProbe() {
        if (!tickLock.tryLock()) {
            return;
        }
        try {
            List<ClaimedService> claimed = claimRepository.claimDue(
                    instanceId, props.leaseTtl().toMillis(), props.claimCap());
            metrics.recordClaim(claimed.size());
            for (ClaimedService service : claimed) {
                probeExecutor.submit(() -> probeOne(service));
            }
        } catch (Exception e) {
            log.warn("claim loop error: {}", e.getMessage());
        } finally {
            tickLock.unlock();
        }
    }

    private void probeOne(ClaimedService service) {
        inflight.acquireUninterruptibly();
        metrics.setInflight(inflightCount());
        try {
            ProbeResult result = probeClient.probe(service.healthUrl(), props.timeout());
            Status newStatus = mapping.map(result);
            int failures = newStatus == Status.DOWN ? service.consecutiveFailures() + 1 : 0;
            boolean transition = newStatus != service.status();
            Instant statusChangedAt = transition ? Instant.now() : service.statusChangedAt();
            long baseMs = newStatus == Status.DOWN
                    ? backoff.nextDelayMs(failures)
                    : props.checkInterval().toMillis();
            long nextDelayMs = backoff.applyJitter(baseMs);
            Instant nextCheckAt = Instant.now().plusMillis(nextDelayMs);
            Integer latency = (int) Math.min(result.latencyMs(), Integer.MAX_VALUE);
            String reason = reasonFor(result);

            WriteBack writeBack = new WriteBack(service.serviceId(), instanceId, newStatus,
                    statusChangedAt, latency, failures, nextCheckAt, props.leaseTtl().toMillis());

            if (!claimRepository.writeBack(writeBack)) {
                log.info("claim lost for {}[{}] (rebalance); discarding probe result", service.key(), service.env());
                metrics.recordClaimExpiry();
                return;
            }

            metrics.recordCheck(service.key(), newStatus.value(), result.latencyMs());
            metrics.updateStatus(service.key(), newStatus.value());

            if (transition) {
                historyRepository.save(StatusHistoryEntity.transition(
                        service.serviceId(), service.status(), newStatus, reason));
                StatusEvent event = StatusEvent.changed(
                        new StatusEvent.ServiceRef(service.serviceId(), service.key(), service.name(), service.env()),
                        service.status().value(), newStatus.value(), Instant.now(), latency, reason);
                log.info("status changed {}[{}]: {} -> {} ({})", service.key(), service.env(),
                        service.status().value(), newStatus.value(), reason == null ? "ok" : reason);
                metrics.recordTransition(service.status().value(), newStatus.value());
                sseBroker.broadcast(event);
                notifyPublisher.publish(event);
            }
            // up-stays-up / down-stays-down are intentionally silent (transition-only logging)
        } catch (Exception e) {
            log.warn("unexpected collector error for {}: {}", service.key(), e.getMessage());
        } finally {
            inflight.release();
            metrics.setInflight(inflightCount());
        }
    }

    private int inflightCount() {
        return props.maxInFlight() - inflight.availablePermits();
    }

    private String reasonFor(ProbeResult result) {
        if (result.isNetworkError()) {
            return result.reason();
        }
        if (!result.is2xx() && !result.isNeverProbed()) {
            return "HTTP " + result.httpStatus();
        }
        return null;
    }
}
