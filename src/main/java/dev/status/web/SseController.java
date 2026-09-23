package dev.status.web;

import dev.status.application.SseBroker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "events", description = "Server-sent status-change stream")
@RequiredArgsConstructor
public class SseController {

    private final SseBroker sseBroker;

    @GetMapping(value = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream status-change events (SSE)")
    @ApiResponse(responseCode = "200", description = "text/event-stream of StatusEvent")
    public ResponseEntity<SseEmitter> events(
            @Parameter(description = "Environment filter (required)") @RequestParam("env") String env) {
        SseEmitter emitter = sseBroker.register(env, new SseEmitter(0L));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .body(emitter);
    }
}
