package dev.status.port;

import dev.status.domain.ApiKeyEntity;

import java.util.Optional;

public interface ApiKeyRepository {

    ApiKeyEntity save(ApiKeyEntity key);

    Optional<ApiKeyEntity> findActiveByKey(String key);
}
