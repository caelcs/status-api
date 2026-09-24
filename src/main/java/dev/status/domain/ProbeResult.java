package dev.status.domain;

/**
 * Result of a single health probe, modelled as a sealed hierarchy of the
 * distinct outcome variants: an HTTP response, a network failure (timeout or
 * connection error), or the never-probed sentinel. Consumers switch over the
 * variants exhaustively (no {@code default}) via type patterns.
 */
public sealed interface ProbeResult
        permits ProbeResult.HttpResult, ProbeResult.NetworkError, ProbeResult.NeverProbed {

    long latencyMs();

    static ProbeResult http(int status, String bodyStatus, long latencyMs) {
        return new HttpResult(status, bodyStatus, latencyMs);
    }

    static ProbeResult error(long latencyMs, String reason) {
        return new NetworkError(latencyMs, reason);
    }

    static ProbeResult neverProbed() {
        return new NeverProbed(0);
    }

    record HttpResult(int status, String bodyStatus, long latencyMs) implements ProbeResult {

        public boolean is2xx() {
            return status >= 200 && status < 300;
        }
    }

    record NetworkError(long latencyMs, String reason) implements ProbeResult {
    }

    record NeverProbed(long latencyMs) implements ProbeResult {
    }
}
