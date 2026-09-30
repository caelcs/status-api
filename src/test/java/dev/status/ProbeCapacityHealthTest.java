package dev.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR10 / AC8 (non-fatal capacity): under oversubscription the readiness probe
 * must stay UP (a status monitor that is reported unready would be rescheduled
 * mid-outage) while the capacity signal surfaces the condition. The overload
 * indicator is excluded from the readiness group in application.yml.
 */
@TestPropertySource(properties = {
        "monitoring.enabled=true",
        "monitoring.claim-tick-ms=400",
        "monitoring.check-interval=3s",
        "monitoring.timeout=2s",
        // oversubscribed: 1000 services × 5s average latency = 5000s >> 10 × 3s = 30s
        "monitoring.expected-max-services=1000",
        "monitoring.expected-probe-latency=5s"
})
class ProbeCapacityHealthTest extends BaseApiTest {

    @Test
    void given_oversubscribedCapacity_when_readiness_then_staysUp_butIndicatorShowsOverload() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        String body = mvc.perform(get("/actuator/health/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .as("the capacity signal must surface the oversubscription without taking readiness DOWN")
                .contains("\"overloaded\":true");
    }
}
