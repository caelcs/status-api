package dev.status.domain;

/**
 * Aggregate counters over a filtered set of services, computed in the query
 * layer (a single grouped/conditional aggregation over the same filter
 * predicate as the paginated list) rather than by loading rows.
 */
public record StatusSummary(int total, int up, int degraded, int down, int unknown) {
}
