package dev.status.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * OpenAPI contract for the SSE event stream. Carries ONLY the springdoc
 * annotations; {@link SseController} keeps the MVC mapping + body.
 */
@Tag(name = "events", description = "Server-sent status-change stream")
public interface SseApi {

    @Operation(summary = "Stream status-change events (SSE)")
    @ApiResponse(responseCode = "200", description = "text/event-stream of StatusEvent")
    ResponseEntity<SseEmitter> events(
            @Parameter(description = "Environment filter (required)") String env);
}
