package dev.status.application;

import dev.status.domain.ServiceEntity;
import dev.status.domain.Status;
import dev.status.domain.StatusHistoryEntity;
import dev.status.dto.ServiceList;
import dev.status.dto.ServiceRegistration;
import dev.status.dto.ServiceStatus;
import dev.status.dto.StatusHistoryResponse;
import dev.status.port.ServiceRepository;
import dev.status.port.StatusHistoryRepository;
import dev.status.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Registration / read / update / delete + history use cases. Enforces env
 * scoping (body env must match the API key's bound env, else 403).
 */
@Service
public class ServiceService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ServiceRepository serviceRepository;
    private final StatusHistoryRepository historyRepository;

    public ServiceService(ServiceRepository serviceRepository, StatusHistoryRepository historyRepository) {
        this.serviceRepository = serviceRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional(readOnly = true)
    public ServiceList list(String env, String statusFilter, String q, String tag, int limit, int offset) {
        if (statusFilter != null && !Status.isValid(statusFilter)) {
            throw ApiException.badRequest("Invalid status filter: " + statusFilter);
        }
        int effectiveLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        int effectiveOffset = Math.max(0, offset);

        Status statusEnum = statusFilter == null ? null : Status.fromValue(statusFilter);
        List<ServiceEntity> filtered = serviceRepository.findByEnv(env).stream()
                .filter(s -> statusEnum == null || s.getStatus() == statusEnum)
                .filter(s -> q == null || q.isBlank() || matchesQuery(s, q))
                .filter(s -> tag == null || tag.isBlank() || (s.getTags() != null && s.getTags().contains(tag)))
                .sorted(Comparator.comparing(ServiceEntity::getKey))
                .toList();

        ServiceList.Summary summary = summarize(filtered);
        List<ServiceStatus> items = filtered.stream()
                .skip(effectiveOffset)
                .limit(effectiveLimit)
                .map(ServiceStatus::from)
                .toList();
        return new ServiceList(items, summary, effectiveLimit, effectiveOffset);
    }

    @Transactional(readOnly = true)
    public ServiceStatus read(UUID id) {
        ServiceEntity entity = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        return ServiceStatus.from(entity);
    }

    @Transactional(readOnly = true)
    public StatusHistoryResponse history(UUID id, Instant since, Instant until, int limit) {
        serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service " + id + " does not exist"));
        int effectiveLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        List<StatusHistoryResponse.StatusHistoryItem> items = historyRepository
                .findByServiceId(id).stream()
                .filter(h -> since == null || !h.getChangedAt().isBefore(since))
                .filter(h -> until == null || !h.getChangedAt().isAfter(until))
                .limit(effectiveLimit)
                .map(h -> new StatusHistoryResponse.StatusHistoryItem(
                        h.getServiceId(),
                        h.getFromStatus() == null ? null : h.getFromStatus().value(),
                        h.getToStatus().value(),
                        h.getChangedAt(),
                        h.getReason()))
                .toList();
        return new StatusHistoryResponse(items, since, until);
    }

    @Transactional
    public ServiceStatus register(ServiceRegistration body, String keyEnv) {
        enforceEnvMatch(body.env(), keyEnv);
        if (serviceRepository.existsByKeyAndEnv(body.key(), body.env())) {
            throw ApiException.conflict(
                    "Service with key '" + body.key() + "' already exists in environment '" + body.env() + "'");
        }
        ServiceEntity entity = ServiceEntity.create(body.key(), body.name(), body.env(), body.healthUrl());
        applyMetadata(entity, body);
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
        existing.setKey(body.key());
        applyMetadata(existing, body);
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

    private void applyMetadata(ServiceEntity entity, ServiceRegistration body) {
        entity.setName(body.name());
        entity.setDescription(body.description());
        entity.setTeam(body.team());
        entity.setHealthUrl(body.healthUrl());
        entity.setTags(body.tags());
    }

    private boolean matchesQuery(ServiceEntity s, String q) {
        String needle = q.toLowerCase();
        return s.getName().toLowerCase().contains(needle) || s.getKey().toLowerCase().contains(needle);
    }

    private ServiceList.Summary summarize(List<ServiceEntity> services) {
        int up = 0, degraded = 0, down = 0, unknown = 0;
        for (ServiceEntity s : services) {
            switch (s.getStatus()) {
                case UP -> up++;
                case DEGRADED -> degraded++;
                case DOWN -> down++;
                case UNKNOWN -> unknown++;
            }
        }
        return new ServiceList.Summary(services.size(), up, degraded, down, unknown);
    }
}
