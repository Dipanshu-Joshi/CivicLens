package com.civicsense.notification;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.civicsense.domain.Channel;
import com.civicsense.domain.DeliveryStatus;
import com.civicsense.domain.Notification;

/** In-app delivery is a persisted notification row; no external provider is used. */
@Component
public class InAppNotificationChannel implements NotificationDeliveryChannel {

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.IN_APP;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void deliver(Notification notification, Instant sentAt) {
        notification.setDeliveryStatus(DeliveryStatus.SENT);
        notification.setSentAt(sentAt);
    }
}
