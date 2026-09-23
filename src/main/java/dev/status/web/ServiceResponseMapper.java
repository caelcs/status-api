package dev.status.web;

import dev.status.dto.ServiceHistory;
import dev.status.dto.ServiceList;
import dev.status.dto.ServiceStatus;
import org.springframework.stereotype.Component;

/**
 * Boundary mapper: application-service result types → wire response DTOs.
 * The controller never returns the service's own types or JPA entities.
 */
@Component
public class ServiceResponseMapper {

    public ServiceStatusResponse toResponse(ServiceStatus s) {
        return new ServiceStatusResponse(
                s.id(),
                s.key(),
                s.name(),
                s.env(),
                s.description(),
                s.team(),
                s.healthUrl(),
                s.tags(),
                s.status(),
                s.statusChangedAt(),
                s.latencyMs(),
                s.lastCheckedAt(),
                s.consecutiveFailures());
    }

    public ServiceListResponse toResponse(ServiceList list) {
        return new ServiceListResponse(
                list.items().stream().map(this::toResponse).toList(),
                new ServiceListResponse.Summary(
                        list.summary().total(),
                        list.summary().up(),
                        list.summary().degraded(),
                        list.summary().down(),
                        list.summary().unknown()),
                list.limit(),
                list.offset());
    }

    public StatusHistoryResponse toResponse(ServiceHistory history) {
        return new StatusHistoryResponse(
                history.items().stream()
                        .map(i -> new StatusHistoryResponse.StatusHistoryItem(
                                i.serviceId(), i.from(), i.to(), i.at(), i.reason()))
                        .toList(),
                history.since(),
                history.until());
    }
}
