package com.civicsense.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civicsense.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    boolean existsByOutboxEventId(UUID outboxEventId);
}
