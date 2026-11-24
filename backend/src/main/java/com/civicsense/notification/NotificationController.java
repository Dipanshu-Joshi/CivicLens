package com.civicsense.notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civicsense.notification.dto.NotificationDtos.NotificationPreferenceRequest;
import com.civicsense.notification.dto.NotificationDtos.NotificationPreferenceResponse;
import com.civicsense.notification.dto.NotificationDtos.NotificationResponse;
import com.civicsense.security.AuthenticatedUser;

import jakarta.validation.Valid;

/** Notifications are always scoped to the authenticated recipient. */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    public NotificationController(
            NotificationService notificationService,
            NotificationPreferenceService preferenceService) {
        this.notificationService = notificationService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.listMine(principal);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Map.of("count", notificationService.unreadCount(principal));
    }

    @GetMapping("/preferences")
    NotificationPreferenceResponse preferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        return preferenceService.get(principal);
    }

    @PatchMapping("/preferences")
    NotificationPreferenceResponse updatePreferences(
            @Valid @RequestBody NotificationPreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return preferenceService.update(principal, request);
    }

    @PatchMapping("/{id}/read")
    Map<String, String> markRead(
            @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markRead(id, principal);
        return Map.of("status", "READ");
    }

    @PostMapping("/read-all")
    Map<String, String> markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markAllRead(principal);
        return Map.of("status", "READ");
    }
}
