package com.civicsense.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.domain.OutboxStatus;
import com.civicsense.observability.CorrelationId;
import com.civicsense.repository.OutboxEventRepository;

/** Scheduled retry sweep plus a public entry point for deterministic tests. */
@Component
@EnableScheduling
public class OutboxDispatcher {

    private static final List<OutboxStatus> RETRYABLE = List.of(
            OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING);

    private final OutboxEventRepository outbox;
    private final OutboxEventProcessor processor;
    private final Clock clock;

    public OutboxDispatcher(
            OutboxEventRepository outbox,
            OutboxEventProcessor processor,
            Clock clock) {
        this.outbox = outbox;
        this.processor = processor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${civicsense.notifications.dispatch-delay-ms:5000}",
            initialDelayString = "${civicsense.notifications.dispatch-initial-delay-ms:5000}")
    public void dispatchScheduled() {
        CorrelationId.beginTask();
        try {
            dispatchDue(50);
        } finally {
            CorrelationId.endTask();
        }
    }

    /** Returns the number of events claimed, not the number successfully delivered. */
    @Transactional(readOnly = true)
    public int dispatchDue(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        Instant now = clock.instant();
        List<UUID> ids = outbox.findDueIds(
                RETRYABLE, OutboxStatus.PENDING, now, PageRequest.of(0, safeLimit));
        for (UUID id : ids) {
            processor.process(id);
        }
        return ids.size();
    }
}
