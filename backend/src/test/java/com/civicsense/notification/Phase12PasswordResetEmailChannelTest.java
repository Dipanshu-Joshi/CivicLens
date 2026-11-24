package com.civicsense.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.civicsense.domain.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** Local unit evidence for provider gating, message shape and log redaction. */
class Phase12PasswordResetEmailChannelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void providerIsDisabledUntilExplicitlyConfigured() throws Exception {
        RecordingMailSender sender = new RecordingMailSender(false);
        PasswordResetEmailChannel channel = channel(sender, false, "", "");

        assertThat(channel.enabled()).isFalse();
        channel.deliver(event(), payload("token-from-disabled-event"));
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void completeConfigurationSendsPlainTextResetLinkWithStableMessageId() throws Exception {
        RecordingMailSender sender = new RecordingMailSender(false);
        PasswordResetEmailChannel channel = channel(sender, true, "smtp.example.test", "no-reply@example.test");
        OutboxEvent event = event();

        assertThat(channel.enabled()).isTrue();
        channel.deliver(event, payload("raw-token-abc"));

        assertThat(sender.sent).hasSize(1);
        MimeMessage message = sender.sent.getFirst();
        assertThat(message.getHeader("Message-ID")[0])
                .isEqualTo("<civicsense-reset-" + event.getId() + "@civicsense.app>");
        assertThat(message.getContent().toString())
                .contains("https://app.example.test/reset-password?token=raw-token-abc")
                .contains("This link expires at 2030-01-01T00:00:00Z");
    }

    @Test
    void transportFailureNeverWritesProviderTextOrTokenToLogs() throws Exception {
        String rawToken = "super-secret-reset-token";
        RecordingMailSender sender = new RecordingMailSender(true);
        PasswordResetEmailChannel channel = channel(sender, true, "smtp.example.test", "no-reply@example.test");

        Logger logger = (Logger) LoggerFactory.getLogger(PasswordResetEmailChannel.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> channel.deliver(event(), payload(rawToken)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Password-reset email delivery failed");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).allSatisfy(message -> assertThat(message).doesNotContain(rawToken));
        assertThat(messages).noneMatch(message -> message.contains("provider said"));
    }

    private static PasswordResetEmailChannel channel(
            RecordingMailSender sender, boolean enabled, String host, String from) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("javaMailSender", (JavaMailSender) sender);
        return new PasswordResetEmailChannel(
                beanFactory.getBeanProvider(JavaMailSender.class), enabled, host, from,
                "CivicSense password reset");
    }

    private static OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        event.setEventType("auth.password_reset_requested");
        return event;
    }

    private static com.fasterxml.jackson.databind.JsonNode payload(String rawToken) throws Exception {
        return MAPPER.readTree("""
                {
                  "email":"resident@example.test",
                  "tokenId":"22222222-2222-4222-8222-222222222222",
                  "resetToken":"%s",
                  "resetUrl":"https://app.example.test/reset-password?token=%s",
                  "expiresAt":"2030-01-01T00:00:00Z"
                }
                """.formatted(rawToken, rawToken));
    }

    private static final class RecordingMailSender extends JavaMailSenderImpl {

        private final boolean fail;
        private final List<MimeMessage> sent = new java.util.ArrayList<>();

        private RecordingMailSender(boolean fail) {
            this.fail = fail;
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            if (fail) {
                throw new MailSendException("provider said token=super-secret-reset-token");
            }
            sent.addAll(List.of(mimeMessages));
        }
    }
}
