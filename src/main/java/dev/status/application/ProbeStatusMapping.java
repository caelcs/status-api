package dev.status.application;

import dev.status.domain.ProbeResult;
import dev.status.domain.Status;
import org.springframework.stereotype.Component;

/**
 * Maps a probe result to a service status (api-contract §5). The mapping is an
 * exhaustive switch over the sealed {@link ProbeResult} variants.
 */
@Component
public final class ProbeStatusMapping {

    public Status map(ProbeResult result) {
        return switch (result) {
            case null -> Status.UNKNOWN;
            case ProbeResult.NeverProbed _ -> Status.UNKNOWN;
            case ProbeResult.NetworkError _ -> Status.DOWN;
            case ProbeResult.HttpResult h when h.is2xx() && "degraded".equalsIgnoreCase(h.bodyStatus()) ->
                    Status.DEGRADED;
            // 2xx + "ok" or missing/other status field -> tolerant up
            case ProbeResult.HttpResult h when h.is2xx() -> Status.UP;
            // non-2xx -> down
            case ProbeResult.HttpResult _ -> Status.DOWN;
        };
    }
}
