package dev.status.application;

import dev.status.domain.ProbeResult;
import dev.status.domain.Status;
import org.springframework.stereotype.Component;

/**
 * Maps a probe result to a service status (api-contract §5).
 */
@Component
public final class ProbeStatusMapping {

    public Status map(ProbeResult result) {
        if (result == null || result.isNeverProbed()) {
            return Status.UNKNOWN;
        }
        if (!result.is2xx()) {
            // non-2xx, timeout, or connection error
            return Status.DOWN;
        }
        if ("degraded".equalsIgnoreCase(result.bodyStatus())) {
            return Status.DEGRADED;
        }
        // 2xx + "ok" or missing/other status field -> tolerant up
        return Status.UP;
    }
}
