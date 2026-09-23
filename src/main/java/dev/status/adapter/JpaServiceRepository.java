package dev.status.adapter;

import dev.status.domain.ServiceEntity;
import dev.status.port.ServiceRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaServiceRepository extends JpaRepository<ServiceEntity, UUID>, ServiceRepository {

    @Override
    Optional<ServiceEntity> findByKeyAndEnv(String key, String env);

    @Override
    List<ServiceEntity> findByEnv(String env);

    @Override
    boolean existsByKeyAndEnv(String key, String env);
}
