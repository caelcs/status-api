package dev.status;

import dev.status.config.MonitoringProperties;
import dev.status.config.ProbeCapacityHealthIndicator;
import dev.status.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the RUNTIME overload signal on {@link ProbeCapacityHealthIndicator}.
 * The {@code overloaded} detail must reflect whether a service is actually behind
 * its schedule ({@code overdueCount() > 0}), not a static config ratio — and a DB
 * outage must surface as {@code overdueServices = -1} (unknown) without throwing
 * out of the health endpoint.
 */
class ProbeCapacityHealthIndicatorTest {

    @Test
    void given_noOverdueServices_when_health_then_overloadedFalse() {
        ClaimRepository repo = mock(ClaimRepository.class);
        when(repo.overdueCount()).thenReturn(0L);

        Health health = new ProbeCapacityHealthIndicator(props(), repo).health();

        assertThat(health.getDetails().get("overloaded")).isEqualTo(false);
        assertThat(health.getDetails().get("overdueServices")).isEqualTo(0L);
    }

    @Test
    void given_overdueServices_when_health_then_overloadedTrue() {
        ClaimRepository repo = mock(ClaimRepository.class);
        when(repo.overdueCount()).thenReturn(3L);

        Health health = new ProbeCapacityHealthIndicator(props(), repo).health();

        assertThat(health.getDetails().get("overloaded")).isEqualTo(true);
        assertThat(health.getDetails().get("overdueServices")).isEqualTo(3L);
    }

    @Test
    void given_dbUnavailable_when_health_then_overdueServicesMinusOne_withoutThrowing() {
        ClaimRepository repo = mock(ClaimRepository.class);
        when(repo.overdueCount()).thenThrow(new RuntimeException("db down"));

        Health health = new ProbeCapacityHealthIndicator(props(), repo).health();

        assertThat(health.getDetails().get("overdueServices")).isEqualTo(-1L);
        assertThat(health.getDetails().get("overloaded")).isEqualTo(false);
    }

    private static MonitoringProperties props() {
        return new MonitoringProperties(
                Duration.ofSeconds(15),   // checkInterval
                Duration.ofSeconds(2),    // timeout
                10,                       // maxInFlight
                50,                       // batchSize
                Duration.ofMillis(5000),  // claimTickMs
                50,                       // expectedMaxServices
                Duration.ofSeconds(1),    // expectedProbeLatency
                1);                       // instanceCount
    }
}
