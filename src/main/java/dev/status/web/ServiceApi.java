package dev.status.web;

import dev.status.dto.ServiceRegistration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * OpenAPI + validation contract for the service endpoints. Carries the
 * springdoc annotations ({@link Operation}, {@link Parameter},
 * {@link ApiResponse}, {@link Tag}) plus the Jakarta Bean Validation parameter
 * constraints — the constraints must live here (not on the controller) so that
 * {@code @Validated} method validation aggregates them across the interface
 * without violating Bean Validation §4.5.5 (an implementing method may not
 * redefine an implemented method's parameter constraint configuration).
 *
 * <p>The implementing controller keeps the Spring MVC mapping + parameter
 * binding annotations and the (thin) method bodies. Spring inherits these
 * interface annotations onto the handler method, so springdoc still documents
 * every operation and its success/error responses.</p>
 */
@Tag(name = "services", description = "Service registration, status, and history")
public interface ServiceApi {

    String STATUS_PATTERN = "(?i)^(up|degraded|down|unknown)$";

    @Operation(summary = "List services (matrix + counters)")
    @ApiResponse(responseCode = "200", description = "ServiceList with summary counters")
    @ApiResponse(responseCode = "400", description = "Invalid query parameter")
    ServiceListResponse list(
            @Parameter(description = "Environment (required)") String env,
            @Parameter(description = "Status filter (up|degraded|down|unknown)")
            @Pattern(regexp = STATUS_PATTERN, message = "status must be one of up|degraded|down|unknown") String status,
            @Parameter(description = "Substring on name/key") String q,
            @Parameter(description = "Exact tag match") String tag,
            @Parameter(description = "Page size (default 50, max 200)")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit,
            @Parameter(description = "Offset")
            @Min(value = 0, message = "offset must be at least 0") int offset);

    @Operation(summary = "Get one service status")
    @ApiResponse(responseCode = "200", description = "ServiceStatus")
    @ApiResponse(responseCode = "404", description = "Service not found")
    ServiceStatusResponse get(@Parameter(description = "Service id") UUID id);

    @Operation(summary = "Get a service's transition history")
    @ApiResponse(responseCode = "200", description = "StatusHistory")
    @ApiResponse(responseCode = "404", description = "Service not found")
    StatusHistoryResponse history(
            @Parameter(description = "Service id") UUID id,
            @Parameter(description = "Lower bound (RFC 3339)") Instant since,
            @Parameter(description = "Upper bound (RFC 3339)") Instant until,
            @Parameter(description = "Max items (default 50, max 200)")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 200, message = "limit must be at most 200") int limit);

    @Operation(summary = "Register a service (idempotent)")
    @ApiResponse(responseCode = "201", description = "Service registered")
    @ApiResponse(responseCode = "400", description = "Malformed body")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "409", description = "Duplicate key in environment")
    ResponseEntity<ServiceStatusResponse> register(
            @Valid ServiceRegistration body,
            @Parameter(hidden = true) String keyEnv);

    @Operation(summary = "Update a service")
    @ApiResponse(responseCode = "200", description = "Service updated")
    @ApiResponse(responseCode = "400", description = "Malformed body")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "404", description = "Service not found")
    ServiceStatusResponse update(
            @Parameter(description = "Service id") UUID id,
            @Valid ServiceRegistration body,
            @Parameter(hidden = true) String keyEnv);

    @Operation(summary = "Delete a service")
    @ApiResponse(responseCode = "204", description = "Service deleted")
    @ApiResponse(responseCode = "401", description = "Missing or unknown API key")
    @ApiResponse(responseCode = "403", description = "API key not authorized for environment")
    @ApiResponse(responseCode = "404", description = "Service not found")
    ResponseEntity<Void> delete(
            @Parameter(description = "Service id") UUID id,
            @Parameter(hidden = true) String keyEnv);
}
