package com.civicsense.phase3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;

import com.civicsense.domain.OutboxEvent;
import com.civicsense.domain.OutboxStatus;
import com.civicsense.notification.OutboxDispatcher;
import com.civicsense.notification.OutboxEventProcessor;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Local end-to-end evidence for SMTP outbox delivery, retry and bounded failure. */
@TestPropertySource(properties = {
        "civicsense.mail.enabled=true",
        "civicsense.mail.host=smtp.example.test",
        "civicsense.mail.from=no-reply@example.test",
        "civicsense.auth.reset-url=https://app.example.test/reset-password"
})
class Phase12PasswordResetEmailIntegrationTest extends Phase3TestSupport {

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private OutboxEventProcessor outboxEventProcessor;

    @Autowired
    private RecordingMailSender mailSender;

    @BeforeEach
    void setUp() {
        resetAndSeed();
        mailSender.sent.clear();
        mailSender.alwaysFail = false;
    }

    @Test
    void successfulDeliveryMarksOutboxProcessedWithoutCreatingInAppNotification() throws Exception {
        forgot(citizenA.getEmail());
        OutboxEvent event = latestResetEvent();

        assertThat(outboxDispatcher.dispatchDue(50)).isPositive();

        assertThat(reload(event).getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(mailSender.sent).hasSize(1);
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(messageBody(mailSender.sent.getFirst()))
                .contains(payload.get("resetToken").asText())
                .contains("https://app.example.test/reset-password?token=");
        assertThat(notifications.findAll()).isEmpty();

        // A second claim cannot resend the same outbox event.
        assertThat(outboxEventProcessor.process(event.getId())).isFalse();
        assertThat(mailSender.sent).hasSize(1);
    }

    @Test
    void transportFailureRetriesAndThenSucceedsWithoutDuplicateDelivery() throws Exception {
        mailSender.alwaysFail = true;
        forgot(citizenA.getEmail());
        OutboxEvent event = latestResetEvent();

        assertThat(outboxDispatcher.dispatchDue(50)).isPositive();
        OutboxEvent failed = reload(event);
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isAfter(Instant.now());
        assertThat(failed.getLastError()).isEqualTo("IllegalStateException");
        assertThat(mailSender.sent).isEmpty();

        mailSender.alwaysFail = false;
        failed.setNextAttemptAt(Instant.now().minusSeconds(1));
        outboxEvents.save(failed);
        assertThat(outboxDispatcher.dispatchDue(50)).isPositive();

        OutboxEvent delivered = reload(event);
        assertThat(delivered.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(delivered.getAttempts()).isEqualTo(2);
        assertThat(mailSender.sent).hasSize(1);
    }

    @Test
    void terminalFailureIsRetainedButNeverReclaimed() throws Exception {
        mailSender.alwaysFail = true;
        forgot(citizenA.getEmail());
        OutboxEvent event = latestResetEvent();

        for (int attempt = 1; attempt <= 5; attempt++) {
            if (attempt > 1) {
                OutboxEvent current = reload(event);
                current.setNextAttemptAt(Instant.now().minusSeconds(1));
                outboxEvents.save(current);
            }
            assertThat(outboxDispatcher.dispatchDue(50)).isPositive();
            assertThat(reload(event).getAttempts()).isEqualTo(attempt);
        }

        OutboxEvent terminal = reload(event);
        assertThat(terminal.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(terminal.getNextAttemptAt()).isNull();
        assertThat(outboxDispatcher.dispatchDue(50)).isZero();
        assertThat(mailSender.sent).isEmpty();
    }

    private void forgot(String email) throws Exception {
        mockMvc.perform(post(BASE + "/auth/forgot-password")
                        .with(request -> {
                            request.setRemoteAddr("10.33.0.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email))))
                .andExpect(status().isOk());
    }

    private OutboxEvent latestResetEvent() {
        return outboxEvents.findAll().stream()
                .filter(event -> "auth.password_reset_requested".equals(event.getEventType()))
                .max(Comparator.comparing(OutboxEvent::getCreatedAt).thenComparing(OutboxEvent::getId))
                .orElseThrow();
    }

    private OutboxEvent reload(OutboxEvent event) {
        return outboxEvents.findById(event.getId()).orElseThrow();
    }

    private static String messageBody(MimeMessage message) throws Exception {
        return message.getContent().toString();
    }

    @TestConfiguration
    static class MailTestConfig {

        @Bean
        @Primary
        RecordingMailSender recordingMailSender() {
            return new RecordingMailSender();
        }
    }

    static final class RecordingMailSender extends JavaMailSenderImpl {

        private final List<MimeMessage> sent = new ArrayList<>();
        private boolean alwaysFail;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            if (alwaysFail) {
                throw new MailSendException("provider unavailable");
            }
            sent.addAll(List.of(mimeMessages));
        }
    }
}
