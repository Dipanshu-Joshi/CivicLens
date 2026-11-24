package com.civicsense.notification;

/** Invalid notification preference request, such as enabling an unconfigured channel. */
public class NotificationPreferenceException extends RuntimeException {

    public NotificationPreferenceException(String message) {
        super(message);
    }
}
