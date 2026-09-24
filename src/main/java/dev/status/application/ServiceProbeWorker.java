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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.Semaphore;

/**
 * Probes a single claimed service: map the result to a status, write it back
 * (unconditionally), and on a transition append history, emit metrics, and
 * broadcast the change over SSE + the Postgres NOTIFY bus.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceProbeWorker {

    private final MonitoringProperties props;
    private final ClaimRepository claimRepository;
    private final StatusHistoryRepository historyRepository;
    private final HealthProbeClient probeClient;
    private final ProbeStatusMapping mapping;
    private final SseBroker sseBroker;
    private final NotifyPublisher notifyPublisher;
    private final MonitoringMetrics metrics;
    private final Semaphore inflight;

    public void probe(ClaimedService service) {
        inflight.acquireUninterruptibly();
        metrics.setInflight(inflightCount());
        try {
            ProbeResult result = probeClient.probe(service.healthUrl(), props.timeout());
            Status newStatus = mapping.map(result);
            int failures = newStatus == Status.DOWN ? service.consecutiveFailures() + 1 : 0;
            boolean transition = newStatus != service.status();
            Instant statusChangedAt = transition ? Instant.now() : service.statusChangedAt();
            Integer latency = (int) Math.min(result.latencyMs(), Integer.MAX_VALUE);
            String reason = reasonFor(result);

            WriteBack writeBack = new WriteBack(service.serviceId(), newStatus,
                    statusChangedAt, latency, failures);
            claimRepository.writeBack(writeBack);

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
        return switch (result) {
            case ProbeResult.NetworkError error -> error.reason();
            case ProbeResult.HttpResult http when !http.is2xx() -> "HTTP " + http.status();
            case ProbeResult.HttpResult _, ProbeResult.NeverProbed _ -> null;
        };
    }
}
