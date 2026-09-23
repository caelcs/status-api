package dev.status.web;

import java.util.List;

/**
 * Matrix + counters response (api-contract §3.2). Distinct from the
 * application-service result type.
 */
public record ServiceListResponse(
        List<ServiceStatusResponse> items,
        Summary summary,
        int limit,
        int offset
) {

    public record Summary(int total, int up, int degraded, int down, int unknown) {
    }
}
