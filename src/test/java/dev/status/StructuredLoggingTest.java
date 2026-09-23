package dev.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import dev.status.application.ServiceProbeWorker;
import dev.status.test.FakeService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StructuredLoggingTest extends MonitoringApiTest {

    @Test
    void given_transition_when_logged_then_transitionsLogged_and_upStaysUpSilent() throws Exception {
        Logger probeWorkerLogger = (Logger) LoggerFactory.getLogger(ServiceProbeWorker.class);
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(ctx);
        appender.start();
        probeWorkerLogger.addAppender(appender);
        try (FakeService fake = new FakeService("up")) {
            String id = register(fake.healthUrl());
            awaitStatus(id, "up", 15);       // unknown -> up (logged)
            Thread.sleep(1500);              // up -> up (must be silent)
            fake.setMode("down");
            awaitStatus(id, "down", 15);     // up -> down (logged)

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(messages)
                    .anyMatch(m -> m.contains("unknown -> up"));
            assertThat(messages)
                    .anyMatch(m -> m.contains("up -> down"));
            assertThat(messages)
                    .noneMatch(m -> m.contains("up -> up"));
        } finally {
            probeWorkerLogger.detachAppender(appender);
        }
    }

    private String register(String healthUrl) throws Exception {
        String body = mvc.perform(post("/api/v1/services")
                        .header("X-API-Key", TestKeys.DEV_KEY)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(registration(unique("svc"), "Service", "dev", healthUrl)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void awaitStatus(String id, String expected, int timeoutSec) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            MvcResult r = mvc.perform(get("/api/v1/services/" + id)).andReturn();
            if (r.getResponse().getStatus() == 200) {
                String current = JsonPath.read(r.getResponse().getContentAsString(), "$.status");
                if (expected.equals(current)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("service did not reach status " + expected);
    }
}
