package dev.status.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Wires the claim-loop collaborators as beans so they are autowired rather than
 * constructed inline (bounded in-flight semaphore + virtual-thread probe
 * executor). Tuning values live in {@link MonitoringProperties}.
 */
@Configuration
public class MonitoringConfiguration {

    @Bean
    public Semaphore monitoringInflightSemaphore(MonitoringProperties props) {
        return new Semaphore(props.maxInFlight());
    }

    @Bean(destroyMethod = "close")
    public ExecutorService monitoringProbeExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
