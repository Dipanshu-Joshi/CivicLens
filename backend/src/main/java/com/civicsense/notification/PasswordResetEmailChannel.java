package com.civicsense.notification;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.civicsense.domain.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.mail.internet.MimeMessage;

/**
 * Delivers password-reset links through SMTP. The channel is inert until an
 * operator supplies a complete provider configuration; raw reset tokens are
 * read only from the durable outbox payload and are never logged.
 */
@Component
public class PasswordResetEmailChannel {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailChannel.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String host;
    private final String from;
    private final String subject;

    public PasswordResetEmailChannel(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${civicsense.mail.enabled:false}") boolean enabled,
            @Value("${civicsense.mail.host:}") String host,
            @Value("${civicsense.mail.from:}") String from,
            @Value("${civicsense.mail.reset-subject:CivicSense password reset}") String subject) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.host = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
        this.subject = subject == null || subject.isBlank() ? "CivicSense password reset" : subject;
    }

    /** True only when the operator explicitly enabled a fully configured provider. */
    public boolean enabled() {
        return enabled && StringUtils.hasText(host) && StringUtils.hasText(from)
                && mailSender.getIfAvailable() != null;
    }

    /** Sends one message for one outbox event; the event id provides a stable Message-ID. */
    public void deliver(OutboxEvent event, JsonNode payload) {
        if (!enabled()) {
            return;
        }
        String recipient = text(payload, "email");
        String resetUrl = text(payload, "resetUrl");
        String expiresAt = text(payload, "expiresAt");
        if (!StringUtils.hasText(recipient) || !StringUtils.hasText(resetUrl)) {
            throw new IllegalArgumentException("Password-reset outbox payload is incomplete");
        }

        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("SMTP provider is unavailable");
            }
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText("Use the link below to reset your CivicSense password.\n\n"
                    + resetUrl + "\n\n"
                    + (StringUtils.hasText(expiresAt) ? "This link expires at " + expiresAt + ".\n\n" : "")
                    + "If you did not request a reset, you can ignore this message.", false);
            // A deterministic Message-ID lets providers that support it suppress a
            // duplicate if the outbox was retried after a transport/DB failure.
            message.setHeader("Message-ID", "<civicsense-reset-" + event.getId() + "@civicsense.app>");
            sender.send(message);
            log.info("Password-reset email delivered for outbox event {}", event.getId());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            // Never log provider exception text: it can contain message content.
            log.warn("Password-reset email delivery failed for outbox event {}", event.getId());
            throw new IllegalStateException("Password-reset email delivery failed");
        }
    }

    private static String text(JsonNode payload, String field) {
        JsonNode value = payload == null ? null : payload.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
