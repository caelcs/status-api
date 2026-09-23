package dev.status.dto;

import java.util.List;

/**
 * Matrix + counters (api-contract §3.2).
 */
public record ServiceList(
        List<ServiceStatus> items,
        Summary summary,
        int limit,
        int offset
) {

    public record Summary(int total, int up, int degraded, int down, int unknown) {
    }
}
