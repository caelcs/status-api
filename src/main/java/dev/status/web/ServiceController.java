package dev.status.web;

import dev.status.application.ServiceCatalogService;
import dev.status.dto.ServiceRegistration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Thin HTTP boundary: bind + validate input, call the application service,
 * map the result to a response DTO. No env derivation, persistence, or
 * business rules live here.
 */
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "services", description = "Service registration, status, and history")
@Validated
@RequiredArgsConstructor
public class ServiceController {

    private static final String STATUS_PATTERN = "(?i)^(up|degraded|down|unknown)$";

    private final ServiceCatalogService service;
    private final ServiceResponseMapper mapper;

    @GetMapping
    @Operation(summary = "List services (matrix + counters)")
    @ApiResponse(responseCode = "200", description = "ServiceList with summary counters")
    @ApiResponse(responseCode = "400", description = "Invalid query parameter")
    public ServiceListResponse list(
            @Parameter(description = "Environment (required)") @RequestParam("env") String env,
            @Parameter(description = "Status filter (up|degraded|down|unknown)")
            @RequestParam(value = "status", required = false)
            @Pattern(regexp = STATUS_PATTERN, message = "status must be one of up|degraded|down|unknown") String status,
            @Parameter(description = "Substring on name/key") @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Exact tag match") @RequestParam(value = "tag", required = false) String tag,
            @Parameter(description = "Page size (default 50, max 200)")
            @RequestParam(value = "limit", required = false, defaultValue = "50")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit,
            @Parameter(description = "Offset")
            @RequestParam(value = "offset", required = false, defaultValue = "0")
            @Min(value = 0, message = "offset must be at least 0") int offset) {
        return mapper.toResponse(service.list(env, status, q, tag, limit, offset));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one service status")
    @ApiResponse(responseCode = "200", description = "ServiceStatus")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ServiceStatusResponse get(@Parameter(description = "Service id") @PathVariable UUID id) {
        return mapper.toResponse(service.read(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get a service's transition history")
    @ApiResponse(responseCode = "200", description = "StatusHistory")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public StatusHistoryResponse history(
            @Parameter(description = "Service id") @PathVariable UUID id,
            @Parameter(description = "Lower bound (RFC 3339)") @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @Parameter(description = "Upper bound (RFC 3339)") @RequestParam(value = "until", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @Parameter(description = "Max items (default 50, max 200)")
            @RequestParam(value = "limit", required = false, defaultValue = "50")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit) {
        return mapper.toResponse(service.history(id, since, until, limit));
    }

    @PostMapping
    @Operation(summary = "Register a service (idempotent)")
    @ApiResponse(responseCode = "201", description = "Service registered")
    @ApiResponse(responseCode = "400", description = "Malformed body")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "409", description = "Duplicate key in environment")
    public ResponseEntity<ServiceStatusResponse> register(
            @Valid @RequestBody ServiceRegistration body,
            @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        return ResponseEntity.status(201).body(mapper.toResponse(service.register(body, keyEnv)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a service")
    @ApiResponse(responseCode = "200", description = "Service updated")
    @ApiResponse(responseCode = "400", description = "Malformed body")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ServiceStatusResponse update(
            @Parameter(description = "Service id") @PathVariable UUID id,
            @Valid @RequestBody ServiceRegistration body,
            @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        return mapper.toResponse(service.update(id, body, keyEnv));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a service")
    @ApiResponse(responseCode = "204", description = "Service deleted")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "404", description = "Service not found")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Service id") @PathVariable UUID id,
            @Parameter(hidden = true) @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        service.delete(id, keyEnv);
        return ResponseEntity.noContent().build();
    }
}
