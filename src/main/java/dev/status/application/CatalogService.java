package dev.status.application;

import dev.status.domain.ServiceEntity;
import dev.status.domain.Status;
import dev.status.domain.StatusSummary;
import dev.status.dto.ServiceHistory;
import dev.status.dto.ServiceList;
import dev.status.dto.ServiceRegistration;
import dev.status.dto.ServiceStatus;
import dev.status.port.ServiceRepository;
import dev.status.port.StatusHistoryRepository;
import dev.status.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registration / read / update / delete + history use cases. Enforces env
 * scoping (body env must match the API key's bound env, else 403). Query
 * parameters are validated at the controller boundary (bean validation).
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ServiceRepository serviceRepository;
    private final StatusHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public ServiceList list(String env, String statusFilter, String q, String tag, int limit, int offset) {
        String status = statusFilter == null ? null : Status.fromValue(statusFilter).value();
        String query = blankToNull(q);
        String tagFilter = blankToNull(tag);
        List<ServiceEntity> page = serviceRepository.search(env, status, query, tagFilter, limit, offset);
        StatusSummary summary = serviceRepository.summarize(env, status, query, tagFilter);
        return new ServiceList(
                page.stream().map(ServiceStatus::from).toList(),
                new ServiceList.Summary(summary.total(), summary.up(), summary.degraded(), summary.down(), summary.unknown()),
                limit,
                offset);
    }

    @Transactional(readOnly = true)
    public ServiceStatus read(UUID id) {
        ServiceEntity entity = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        return ServiceStatus.from(entity);
    }

    @Transactional(readOnly = true)
    public ServiceHistory history(UUID id, Instant since, Instant until, int limit) {
        serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        List<ServiceHistory.StatusHistoryItem> items = historyRepository
                .findHistory(id, since, until, limit).stream()
                .map(h -> new ServiceHistory.StatusHistoryItem(
                        h.getServiceId(),
                        h.getFromStatus() == null ? null : h.getFromStatus().value(),
                        h.getToStatus().value(),
                        h.getChangedAt(),
                        h.getReason()))
                .toList();
        return new ServiceHistory(items, since, until);
    }

    @Transactional
    public ServiceStatus register(ServiceRegistration body, String keyEnv) {
        enforceEnvMatch(body.env(), keyEnv);
        if (serviceRepository.existsByKeyAndEnv(body.key(), body.env())) {
            throw ApiException.conflict(
                    "Service with key '" + body.key() + "' already exists in environment '" + body.env() + "'");
        }
        ServiceEntity entity = ServiceEntity.create(
                body.key(), body.name(), body.env(), body.description(), body.team(), body.healthUrl(), body.tags());
        return ServiceStatus.from(serviceRepository.save(entity));
    }

    @Transactional
    public ServiceStatus update(UUID id, ServiceRegistration body, String keyEnv) {
        enforceEnvMatch(body.env(), keyEnv);
        ServiceEntity existing = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        if (!existing.getEnv().equals(body.env())) {
            throw ApiException.forbidden("API key is not authorized for environment " + body.env());
        }
        if (!existing.getKey().equals(body.key())
                && serviceRepository.existsByKeyAndEnv(body.key(), body.env())) {
            throw ApiException.conflict(
                    "Service with key '" + body.key() + "' already exists in environment '" + body.env() + "'");
        }
        existing.applyRegistration(
                body.key(), body.name(), body.healthUrl(), body.description(), body.team(), body.tags());
        return ServiceStatus.from(serviceRepository.save(existing));
    }

    @Transactional
    public void delete(UUID id, String keyEnv) {
        ServiceEntity existing = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        if (!existing.getEnv().equals(keyEnv)) {
            throw ApiException.forbidden("API key is not authorized for environment " + existing.getEnv());
        }
        serviceRepository.delete(existing);
    }

    private void enforceEnvMatch(String bodyEnv, String keyEnv) {
        if (!bodyEnv.equals(keyEnv)) {
            throw ApiException.forbidden("API key is not authorized for environment " + bodyEnv);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
