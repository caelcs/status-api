package dev.status.adapter;

import dev.status.domain.ApiKeyEntity;
import dev.status.port.ApiKeyRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID>, ApiKeyRepository {

    @Override
    @Query("select k from ApiKeyEntity k where k.key = :key and k.revokedAt is null")
    Optional<ApiKeyEntity> findActiveByKey(@Param("key") String key);
}
