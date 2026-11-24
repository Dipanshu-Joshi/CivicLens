package com.civicsense.notification;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.domain.NotificationPreference;
import com.civicsense.notification.dto.NotificationDtos.NotificationPreferenceRequest;
import com.civicsense.notification.dto.NotificationDtos.NotificationPreferenceResponse;
import com.civicsense.repository.NotificationPreferenceRepository;
import com.civicsense.security.AuthenticatedUser;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse get(AuthenticatedUser principal) {
        return toResponse(repository.findById(principal.id()).orElseGet(() -> defaults(principal.id())));
    }

    @Transactional
    public NotificationPreferenceResponse update(
            AuthenticatedUser principal, NotificationPreferenceRequest request) {
        if (Boolean.TRUE.equals(request.emailEnabled()) || Boolean.TRUE.equals(request.pushEnabled())) {
            throw new NotificationPreferenceException(
                    "Email and push channels are not configured and cannot be enabled");
        }
        if (request.inAppEnabled() == null) {
            throw new NotificationPreferenceException("inAppEnabled is required");
        }
        NotificationPreference preference = repository.findById(principal.id())
                .orElseGet(() -> defaults(principal.id()));
        Instant now = clock.instant();
        if (preference.getCreatedAt() == null) {
            preference.setCreatedAt(now);
        }
        preference.setInAppEnabled(request.inAppEnabled());
        preference.setEmailEnabled(false);
        preference.setPushEnabled(false);
        preference.setUpdatedAt(now);
        return toResponse(repository.save(preference));
    }

    private NotificationPreference defaults(java.util.UUID userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setPushEnabled(false);
        Instant now = clock.instant();
        preference.setCreatedAt(now);
        preference.setUpdatedAt(now);
        return preference;
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.isInAppEnabled(), preference.isEmailEnabled(), preference.isPushEnabled());
    }
}
