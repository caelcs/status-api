package dev.status.port;

import dev.status.domain.ServiceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository {

    ServiceEntity save(ServiceEntity service);

    Optional<ServiceEntity> findById(UUID id);

    Optional<ServiceEntity> findByKeyAndEnv(String key, String env);

    List<ServiceEntity> findByEnv(String env);

    boolean existsByKeyAndEnv(String key, String env);

    void delete(ServiceEntity service);
}
