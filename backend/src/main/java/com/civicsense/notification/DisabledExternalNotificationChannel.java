package com.civicsense.notification;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.civicsense.domain.Channel;
import com.civicsense.domain.Notification;

/**
 * Placeholder for future email/push providers. No outbound integration is
 * configured, so these channels are always disabled and never selected.
 */
@Component
public class DisabledExternalNotificationChannel implements NotificationDeliveryChannel {

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL || channel == Channel.PUSH;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void deliver(Notification notification, Instant sentAt) {
        throw new UnsupportedOperationException("External notification channels are not configured");
    }
}
