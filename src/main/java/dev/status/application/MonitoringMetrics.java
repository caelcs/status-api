package dev.status.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics (PRD §2.4 / ADR §4.6).
 */
@Component
@RequiredArgsConstructor
public class MonitoringMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger inflight = new AtomicInteger(0);
    private final Map<String, Double> upByService = new ConcurrentHashMap<>();

    private MultiGauge statusUp;
    private Timer checkDuration;

    @PostConstruct
    void init() {
        this.statusUp = MultiGauge.builder("status_up")
                .description("1 if the service is currently up, else 0")
                .register(registry);
        Gauge.builder("status_inflight_checks", inflight, AtomicInteger::get)
                .description("Current number of in-flight health checks")
                .register(registry);
        this.checkDuration = Timer.builder("status_check_duration_seconds")
                .description("Duration of health checks")
                .register(registry);
    }

    public void recordCheck(String service, String result, long durationMs) {
        registry.counter("status_checks_total", "service", service, "result", result).increment();
        checkDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTransition(String from, String to) {
        registry.counter("status_transitions_total", "from", from, "to", to).increment();
    }

    public void recordClaim(int count) {
        if (count > 0) {
            registry.counter("status_claims_total").increment(count);
        }
    }

    public void updateStatus(String service, String status) {
        upByService.put(service, "up".equals(status) ? 1.0 : 0.0);
        statusUp.register(upByService.entrySet().stream()
                .map(e -> MultiGauge.Row.of(Tags.of("service", e.getKey()), e.getValue()))
                .toList());
    }

    public void setInflight(int n) {
        inflight.set(n);
    }
}
