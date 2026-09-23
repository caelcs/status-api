package dev.status.application;

import dev.status.domain.ProbeResult;
import dev.status.domain.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeStatusMappingTest {

    private final ProbeStatusMapping mapping = new ProbeStatusMapping();

    @Nested
    @DisplayName("probe -> status mapping")
    class Mapping {

        @Test
        void given_2xxWithOk_when_mapped_then_up() {
            assertThat(mapping.map(ProbeResult.http(200, "ok", 12))).isEqualTo(Status.UP);
        }

        @Test
        void given_2xxWithDegraded_when_mapped_then_degraded() {
            assertThat(mapping.map(ProbeResult.http(200, "degraded", 30))).isEqualTo(Status.DEGRADED);
        }

        @Test
        void given_2xxWithMissingStatus_when_mapped_then_upTolerant() {
            assertThat(mapping.map(ProbeResult.http(200, null, 5))).isEqualTo(Status.UP);
        }

        @Test
        void given_2xxWithUnknownField_when_mapped_then_upTolerant() {
            assertThat(mapping.map(ProbeResult.http(204, "weird", 5))).isEqualTo(Status.UP);
        }

        @Test
        void given_non2xx_when_mapped_then_down() {
            assertThat(mapping.map(ProbeResult.http(500, null, 5))).isEqualTo(Status.DOWN);
        }

        @Test
        void given_timeout_when_mapped_then_down() {
            assertThat(mapping.map(ProbeResult.error(2000, "timeout"))).isEqualTo(Status.DOWN);
        }

        @Test
        void given_connectionError_when_mapped_then_down() {
            assertThat(mapping.map(ProbeResult.error(12, "connect timeout"))).isEqualTo(Status.DOWN);
        }

        @Test
        void given_neverProbed_when_mapped_then_unknown() {
            assertThat(mapping.map(ProbeResult.neverProbed())).isEqualTo(Status.UNKNOWN);
        }

        @Test
        void given_null_when_mapped_then_unknown() {
            assertThat(mapping.map(null)).isEqualTo(Status.UNKNOWN);
        }
    }
}
