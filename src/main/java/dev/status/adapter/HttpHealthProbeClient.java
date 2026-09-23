package dev.status.adapter;

import dev.status.domain.ProbeResult;
import dev.status.port.HealthProbeClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single GET per check with a hard timeout; tolerant JSON parse (extracts the
 * optional "status" field). Never throws — failures become a DOWN result.
 */
@Component
public class HttpHealthProbeClient implements HealthProbeClient {

    private static final Pattern STATUS_FIELD = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public ProbeResult probe(String healthUrl, Duration timeout) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = elapsedMs(start);
            return ProbeResult.http(response.statusCode(), parseStatus(response.body()), latency);
        } catch (Exception e) {
            long latency = elapsedMs(start);
            return ProbeResult.error(latency, reasonFor(e));
        }
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private String parseStatus(String body) {
        if (body == null) {
            return null;
        }
        Matcher m = STATUS_FIELD.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private String reasonFor(Exception e) {
        if (e instanceof HttpTimeoutException) {
            return "timeout";
        }
        if (e instanceof ConnectException) {
            return "connect timeout";
        }
        if (e instanceof IOException) {
            return "connection error";
        }
        return e.getClass().getSimpleName();
    }
}
