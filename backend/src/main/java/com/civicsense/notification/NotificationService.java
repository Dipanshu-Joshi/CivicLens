package com.civicsense.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.complaint.exception.ComplaintExceptions.ComplaintNotFoundException;
import com.civicsense.domain.Channel;
import com.civicsense.domain.DeliveryStatus;
import com.civicsense.domain.Notification;
import com.civicsense.domain.NotificationType;
import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.identity.UserRepository;
import com.civicsense.notification.dto.NotificationDtos.NotificationResponse;
import com.civicsense.repository.ComplaintRepository;
import com.civicsense.repository.NotificationRepository;
import com.civicsense.repository.OutboxEventRepository;
import com.civicsense.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists notification intents as durable outbox rows. Actual in-app delivery
 * happens after commit in {@link OutboxEventProcessor}, so a delivery failure
 * cannot roll back the complaint workflow that produced the event.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationService(
            NotificationRepository notificationRepository,
            OutboxEventRepository outboxEventRepository,
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            ApplicationEventPublisher events,
            ObjectMapper objectMapper,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Called by other services; only the outbox row is written in this transaction. */
    public Notification notify(UUID recipientId, UUID complaintId, NotificationType type,
            String title, String body) {
        if (recipientId == null) {
            return null;
        }
        Instant now = clock.instant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientUserId", recipientId.toString());
        payload.put("complaintId", complaintId == null ? null : complaintId.toString());
        payload.put("type", type.name());
        payload.put("title", title == null ? "" : title);
        payload.put("body", body == null ? "" : body);
        payload.put("dedupeKey", UUID.randomUUID().toString());

        OutboxEvent outbox = new OutboxEvent();
        outbox.setEventType("notification." + type.name().toLowerCase());
        outbox.setAggregateType(complaintId == null ? "user" : "complaint");
        outbox.setAggregateId((complaintId == null ? recipientId : complaintId).toString());
        outbox.setPayload(writePayload(payload));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setIdempotencyKey(UUID.randomUUID().toString());
        outbox.setCreatedAt(now);
        OutboxEvent savedOutbox = outboxEventRepository.save(outbox);
        events.publishEvent(new NotificationOutboxPublishedEvent(savedOutbox.getId()));
        return null;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(AuthenticatedUser principal) {
        return notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(principal.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser principal) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(principal.id());
    }

    @Transactional
    public void markRead(UUID notificationId, AuthenticatedUser principal) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(item -> item.getRecipient().getId().equals(principal.id()))
                .orElseThrow(ComplaintNotFoundException::new);
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
            notification.setDeliveryStatus(DeliveryStatus.READ);
        }
    }

    @Transactional
    public void markAllRead(AuthenticatedUser principal) {
        notificationRepository.findAllByRecipientIdOrderByCreatedAtDesc(principal.id())
                .forEach(notification -> {
                    if (notification.getReadAt() == null) {
                        notification.setReadAt(clock.instant());
                        notification.setDeliveryStatus(DeliveryStatus.READ);
                    }
                });
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getChannel(),
                notification.getDeliveryStatus(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getComplaint() == null ? null : notification.getComplaint().getId(),
                notification.getComplaint() == null ? null : notification.getComplaint().getComplaintNumber());
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize notification outbox payload", exception);
        }
    }
}
