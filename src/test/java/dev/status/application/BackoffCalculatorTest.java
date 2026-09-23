package dev.status.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {

    private final BackoffCalculator backoff =
            new BackoffCalculator(List.of(15_000L, 30_000L, 60_000L), 0.2, new Random(42));

    @Nested
    @DisplayName("backoff intervals")
    class Intervals {

        @Test
        void given_zeroFailures_when_nextDelay_then_15s() {
            assertThat(backoff.nextDelayMs(0)).isEqualTo(15_000L);
        }

        @Test
        void given_oneFailure_when_nextDelay_then_15s() {
            assertThat(backoff.nextDelayMs(1)).isEqualTo(15_000L);
        }

        @Test
        void given_twoFailures_when_nextDelay_then_30s() {
            assertThat(backoff.nextDelayMs(2)).isEqualTo(30_000L);
        }

        @Test
        void given_threeFailures_when_nextDelay_then_60s() {
            assertThat(backoff.nextDelayMs(3)).isEqualTo(60_000L);
        }

        @Test
        void given_manyFailures_when_nextDelay_then_cappedAt60s() {
            assertThat(backoff.nextDelayMs(10)).isEqualTo(60_000L);
        }

        @Test
        void given_success_when_reset_then_15s() {
            assertThat(backoff.nextDelayMs(0)).isEqualTo(15_000L);
        }
    }

    @Nested
    @DisplayName("jitter bounds")
    class Jitter {

        @Test
        void given_baseDelay_when_jitterApplied_then_withinBounds() {
            for (int i = 0; i < 1000; i++) {
                long jittered = backoff.applyJitter(15_000L);
                assertThat(jittered).isBetween(12_000L, 18_000L); // +/- 20%
            }
        }

        @Test
        void given_baseDelay_when_jitterApplied_then_positive() {
            for (int i = 0; i < 100; i++) {
                assertThat(backoff.applyJitter(1L)).isGreaterThanOrEqualTo(1L);
            }
        }
    }
}
