package dev.status.application;

import java.util.List;
import java.util.Random;

/**
 * Per-service exponential backoff (15s -> 30s -> 60s capped) with jitter.
 * One success resets the count, so the next delay is the base interval.
 */
public final class BackoffCalculator {

    private final List<Long> backoffMs;
    private final double jitterBound;
    private final Random random;

    public BackoffCalculator(List<Long> backoffMs, double jitterBound) {
        this(backoffMs, jitterBound, new Random());
    }

    public BackoffCalculator(List<Long> backoffMs, double jitterBound, Random random) {
        if (backoffMs == null || backoffMs.isEmpty()) {
            throw new IllegalArgumentException("backoffMs must be non-empty");
        }
        this.backoffMs = backoffMs;
        this.jitterBound = jitterBound;
        this.random = random;
    }

    /**
     * Base delay for the given number of consecutive failures.
     * 0 (healthy/reset) -> base; 1 -> base; then capped at the last entry.
     */
    public long nextDelayMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return backoffMs.get(0);
        }
        int idx = Math.min(consecutiveFailures - 1, backoffMs.size() - 1);
        return backoffMs.get(idx);
    }

    /** Applies symmetric multiplicative jitter to a base delay. */
    public long applyJitter(long baseMs) {
        double factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * jitterBound;
        return Math.max(1L, Math.round(baseMs * factor));
    }
}
