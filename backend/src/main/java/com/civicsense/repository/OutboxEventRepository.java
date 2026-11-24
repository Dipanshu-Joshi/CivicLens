package com.civicsense.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * New PENDING rows have a null nextAttemptAt; terminal FAILED rows also have
     * a null nextAttemptAt and must never be reclaimed. The explicit pending
     * status parameter preserves both behaviors without a schema migration.
     */
    @Query("""
            select o.id from OutboxEvent o
            where o.status in :statuses
              and ((o.status = :pendingStatus and o.nextAttemptAt is null)
                   or (o.nextAttemptAt is not null and o.nextAttemptAt <= :now))
            order by o.createdAt asc
            """)
    List<UUID> findDueIds(@Param("statuses") Collection<OutboxStatus> statuses,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("now") Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent o
            set o.status = com.civicsense.domain.OutboxStatus.PROCESSING,
                o.attempts = o.attempts + 1,
                o.nextAttemptAt = :leaseUntil,
                o.lastError = null
            where o.id = :id
              and o.status in :statuses
              and ((o.status = :pendingStatus and o.nextAttemptAt is null)
                   or (o.nextAttemptAt is not null and o.nextAttemptAt <= :now))
            """)
    int claim(@Param("id") UUID id, @Param("statuses") Collection<OutboxStatus> statuses,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
