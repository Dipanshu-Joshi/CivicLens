package com.civicsense.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.domain.Channel;
import com.civicsense.domain.DeliveryStatus;
import com.civicsense.domain.Notification;
import com.civicsense.domain.NotificationPreference;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.identity.UserRepository;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.NotificationPreferenceRepository;
import com.civicsense.repository.NotificationRepository;
import com.civicsense.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts one durable outbox event into at most one in-app notification.
 * Each event runs in its own transaction so one failure cannot roll back the
 * complaint workflow or block other queued events.
 */
@Component
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);
    private static final List<OutboxStatus> CLAIMABLE = List.of(
            OutboxStatus.PENDING, OutboxStatus.FAILED, OutboxStatus.PROCESSING);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);

    private final OutboxEventRepository outbox;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final UserRepository users;
    private final ComplaintRepository complaints;
    private final List<NotificationDeliveryChannel> channels;
    private final PasswordResetEmailChannel passwordResetEmail;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventProcessor(
            OutboxEventRepository outbox,
            NotificationRepository notifications,
            NotificationPreferenceRepository preferences,
            UserRepository users,
            ComplaintRepository complaints,
            List<NotificationDeliveryChannel> channels,
            PasswordResetEmailChannel passwordResetEmail,
            ObjectMapper objectMapper,
            Clock clock) {
        this.outbox = outbox;
        this.notifications = notifications;
        this.preferences = preferences;
        this.users = users;
        this.complaints = complaints;
        this.channels = channels;
        this.passwordResetEmail = passwordResetEmail;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Returns true when this invocation claimed and processed the event. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(UUID eventId) {
        Instant now = clock.instant();
        if (outbox.claim(eventId, CLAIMABLE, OutboxStatus.PENDING, now, now.plus(LEASE)) == 0) {
            return false;
        }
        OutboxEvent event = outbox.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        if ("auth.password_reset_requested".equals(event.getEventType())) {
            return processPasswordResetEmail(event, now);
        }
        if (!event.getEventType().startsWith("notification.")) {
            // Unknown auth/system events are consumed without creating an in-app
            // notification, which would leak raw reset tokens to the UI.
            markProcessed(event, now);
            return true;
        }
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            UUID recipientId = UUID.fromString(required(payload, "recipientUserId"));
            NotificationPreference preference = preferences.findById(recipientId).orElse(null);
            if (preference != null && !preference.isInAppEnabled()) {
                markProcessed(event, now);
                return true;
            }
            UUID complaintId = optionalUuid(payload, "complaintId");
            NotificationType type = NotificationType.valueOf(required(payload, "type"));
            String title = text(payload, "title");
            String body = text(payload, "body");

            if (!notifications.existsByOutboxEventId(eventId)) {
                Notification notification = new Notification();
                notification.setRecipient(users.getReferenceById(recipientId));
                if (complaintId != null) {
                    notification.setComplaint(complaints.getReferenceById(complaintId));
                }
                notification.setType(type);
                notification.setTitle(title);
                notification.setBody(body);
                notification.setChannel(Channel.IN_APP);
                notification.setDeliveryStatus(DeliveryStatus.PENDING);
                notification.setOutboxEventId(eventId);
                notification.setCreatedAt(now);
                NotificationDeliveryChannel channel = channels.stream()
                        .filter(item -> item.supports(Channel.IN_APP) && item.enabled())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No enabled in-app channel"));
                channel.deliver(notification, now);
                notifications.save(notification);
            }
            markProcessed(event, now);
            return true;
        } catch (Exception exception) {
            markFailed(event, now, exception);
            return false;
        }
    }

    private boolean processPasswordResetEmail(OutboxEvent event, Instant now) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            if (!passwordResetEmail.enabled()) {
                // Provider-disabled events are consumed; no fake delivery is claimed.
                markProcessed(event, now);
                return true;
            }
            passwordResetEmail.deliver(event, payload);
            markProcessed(event, now);
            return true;
        } catch (Exception exception) {
            markFailed(event, now, exception);
            return false;
        }
    }

    private void markProcessed(OutboxEvent event, Instant now) {
        event.setStatus(OutboxStatus.PROCESSED);
        event.setNextAttemptAt(null);
        event.setLastError(null);
        event.setProcessedAt(now);
        outbox.save(event);
    }

    private void markFailed(OutboxEvent event, Instant now, Exception exception) {
        String reason = safeError(exception);
        // Exception text can contain tokens, complaint content or provider data;
        // only the event id and exception class are safe to log.
        log.warn("Outbox event {} delivery failed ({})", event.getId(), reason);
        int attempts = event.getAttempts();
        event.setStatus(OutboxStatus.FAILED);
        event.setLastError(reason);
        event.setNextAttemptAt(attempts >= MAX_ATTEMPTS ? null : now.plus(RETRY_BACKOFF));
        outbox.save(event);
    }

    private String safeError(Exception exception) {
        return exception.getClass().getSimpleName();
    }

    private String required(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing outbox field " + field);
        }
        return value.asText();
    }

    private String text(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private UUID optionalUuid(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : UUID.fromString(value.asText());
    }
}
