package dev.status.adapter;

import dev.status.domain.StatusHistoryEntity;
import dev.status.port.StatusHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JpaStatusHistoryRepository extends JpaRepository<StatusHistoryEntity, Long>, StatusHistoryRepository {

    @Override
    @Query("""
            select h from StatusHistoryEntity h
            where h.serviceId = :serviceId
            order by h.changedAt desc
            """)
    List<StatusHistoryEntity> findByServiceId(@Param("serviceId") UUID serviceId);
}
