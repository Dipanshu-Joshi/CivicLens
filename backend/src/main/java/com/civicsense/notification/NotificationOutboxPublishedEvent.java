package com.civicsense.notification;

import java.util.UUID;

/** Published after an outbox row is saved so dispatch can run after commit. */
public record NotificationOutboxPublishedEvent(UUID outboxEventId) {
}
