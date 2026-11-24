package com.civicsense.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers newly committed notification events without blocking the workflow request. */
@Component
public class NotificationOutboxListener {

    private final OutboxEventProcessor processor;

    public NotificationOutboxListener(OutboxEventProcessor processor) {
        this.processor = processor;
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(NotificationOutboxPublishedEvent event) {
        processor.process(event.outboxEventId());
    }
}
