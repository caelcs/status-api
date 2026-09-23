package dev.status.web;

import dev.status.application.ServiceCatalogService;
import dev.status.dto.ServiceRegistration;
import jakarta.validation.Valid;
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
 * business rules live here. The springdoc/OpenAPI annotations and the Jakarta
 * parameter constraints live on {@link ServiceApi}; this class keeps the Spring
 * MVC mapping + parameter binding annotations and the (thin) method bodies.
 */
@RestController
@RequestMapping("/api/v1/services")
@Validated
@RequiredArgsConstructor
public class ServiceController implements ServiceApi {

    private final ServiceCatalogService service;
    private final ServiceResponseMapper mapper;

    @GetMapping
    public ServiceListResponse list(
            @RequestParam("env") String env,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset) {
        return mapper.toResponse(service.list(env, status, q, tag, limit, offset));
    }

    @GetMapping("/{id}")
    public ServiceStatusResponse get(@PathVariable UUID id) {
        return mapper.toResponse(service.read(id));
    }

    @GetMapping("/{id}/history")
    public StatusHistoryResponse history(
            @PathVariable UUID id,
            @RequestParam(value = "since", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(value = "until", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        return mapper.toResponse(service.history(id, since, until, limit));
    }

    @PostMapping
    public ResponseEntity<ServiceStatusResponse> register(
            @Valid @RequestBody ServiceRegistration body,
            @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        return ResponseEntity.status(201).body(mapper.toResponse(service.register(body, keyEnv)));
    }

    @PutMapping("/{id}")
    public ServiceStatusResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceRegistration body,
            @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        return mapper.toResponse(service.update(id, body, keyEnv));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestAttribute(ApiKeyAuthFilter.AUTH_ENV_ATTR) String keyEnv) {
        service.delete(id, keyEnv);
        return ResponseEntity.noContent().build();
    }
}
