package dev.status.application;

import dev.status.port.InstanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Heartbeats this instance's membership row (FR11 §6). Stale heartbeats mark an
 * instance dead; its claims become claimable when their lease expires.
 */
@Component
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class InstanceHeartbeat {

    private final InstanceRepository instanceRepository;
    private final String instanceId;
    private final Instant startedAt;

    public InstanceHeartbeat(InstanceRepository instanceRepository, InstanceIdentity identity) {
        this.instanceRepository = instanceRepository;
        this.instanceId = identity.id();
        this.startedAt = Instant.now();
    }

    @Scheduled(fixedDelayString = "${monitoring.heartbeat-interval-ms:10000}")
    public void heartbeat() {
        instanceRepository.heartbeat(instanceId, startedAt);
    }
}
