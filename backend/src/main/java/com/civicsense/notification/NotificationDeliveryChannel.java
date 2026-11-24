package com.civicsense.notification;

import java.time.Instant;

import com.civicsense.domain.Channel;
import com.civicsense.domain.Notification;

/**
 * Delivery abstraction for notification channels. External channels are
 * represented but disabled until a real provider is configured.
 */
public interface NotificationDeliveryChannel {

    boolean supports(Channel channel);

    boolean enabled();

    void deliver(Notification notification, Instant sentAt);
}
