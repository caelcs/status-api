package dev.status.domain;

/**
 * Result of a single health probe. {@link #httpStatus()} is the raw HTTP status
 * code (2xx/non-2xx), {@code -1} for a network error/timeout, or
 * {@link #NEVER} for a service that has never been probed.
 */
public record ProbeResult(
        int httpStatus,
        String bodyStatus,
        long latencyMs,
        String reason
) {

    public static final int NEVER = -2;
    public static final int NETWORK_ERROR = -1;

    public static ProbeResult http(int status, String bodyStatus, long latencyMs) {
        return new ProbeResult(status, bodyStatus, latencyMs, null);
    }

    public static ProbeResult error(long latencyMs, String reason) {
        return new ProbeResult(NETWORK_ERROR, null, latencyMs, reason);
    }

    public static ProbeResult neverProbed() {
        return new ProbeResult(NEVER, null, 0, null);
    }

    public boolean isNeverProbed() {
        return httpStatus == NEVER;
    }

    public boolean is2xx() {
        return httpStatus >= 200 && httpStatus < 300;
    }

    public boolean isNetworkError() {
        return httpStatus == NETWORK_ERROR;
    }
}
