package com.civicsense.notification.dto;

import java.time.Instant;
import java.util.UUID;

import com.civicsense.domain.Channel;
import com.civicsense.domain.DeliveryStatus;
import com.civicsense.domain.NotificationType;

import jakarta.validation.constraints.NotNull;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            NotificationType type,
            String title,
            String body,
            Channel channel,
            DeliveryStatus deliveryStatus,
            Instant readAt,
            Instant createdAt,
            Instant sentAt,
            UUID complaintId,
            String complaintNumber) {
    }

    public record NotificationPreferenceResponse(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean pushEnabled) {
    }

    public record NotificationPreferenceRequest(
            @NotNull Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean pushEnabled) {
    }
}
