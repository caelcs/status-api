package dev.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.status.config.MonitoringProperties;
import dev.status.config.MonitoringPropertiesValidator;
import dev.status.config.ProbeCapacityHealthIndicator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boot validation for the monitoring configuration (analysis-probe-overload.md
 * Alt 1): impossible values hard-fail startup with a clear message; capacity
 * oversubscription is a non-fatal WARN that is surfaced, not a startup failure.
 */
class MonitoringConfigValidationTest {

    @Configuration
    @EnableConfigurationProperties(MonitoringProperties.class)
    static class Config {
        @Bean
        MonitoringPropertiesValidator validator(MonitoringProperties props) {
            return new MonitoringPropertiesValidator(props);
        }

        @Bean
        ProbeCapacityHealthIndicator probeCapacity(MonitoringProperties props) {
            return new ProbeCapacityHealthIndicator(props);
        }
    }

    private ApplicationContextRunner runner(String... props) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(props);
    }

    @Test
    void given_zeroCheckInterval_when_booted_then_failsToStart() {
        runner("monitoring.check-interval=0s").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_zeroTimeout_when_booted_then_failsToStart() {
        runner("monitoring.timeout=0s").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_zeroMaxInFlight_when_booted_then_failsToStart() {
        runner("monitoring.max-in-flight=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_zeroBatchSize_when_booted_then_failsToStart() {
        runner("monitoring.batch-size=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_zeroClaimTick_when_booted_then_failsToStart() {
        runner("monitoring.claim-tick-ms=0").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_checkIntervalNotGreaterThanTimeout_when_booted_then_failsToStart() {
        runner("monitoring.check-interval=1s", "monitoring.timeout=2s")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_claimTickNotLessThanCheckInterval_when_booted_then_failsToStart() {
        runner("monitoring.check-interval=15s", "monitoring.claim-tick-ms=15s")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void given_oversubscribedCapacity_when_booted_then_startsWarnsAndSurfacesDetail() {
        Logger logger = (Logger) LoggerFactory.getLogger(MonitoringPropertiesValidator.class);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        try {
            runner("monitoring.expected-max-services=1000", "monitoring.expected-probe-latency=5s")
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed();
                        ProbeCapacityHealthIndicator indicator = ctx.getBean(ProbeCapacityHealthIndicator.class);
                        assertThat(indicator.health().getDetails().get("overloaded"))
                                .as("capacity oversubscription must be surfaced via the health detail")
                                .isEqualTo(true);
                    });
            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertThat(messages)
                    .as("capacity oversubscription must produce a loud WARN, not a startup failure")
                    .anyMatch(m -> m.contains("oversubscribed"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
