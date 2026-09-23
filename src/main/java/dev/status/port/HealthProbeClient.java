package dev.status.port;

import dev.status.domain.ProbeResult;

import java.time.Duration;

public interface HealthProbeClient {

    /** A single GET against the health URL with a hard timeout. Never throws. */
    ProbeResult probe(String healthUrl, Duration timeout);
}
