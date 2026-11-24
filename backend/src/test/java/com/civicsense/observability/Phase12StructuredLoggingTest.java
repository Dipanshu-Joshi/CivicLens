package com.civicsense.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;

/** Verifies that the runtime console appender uses Boot's structured JSON encoder. */
@SpringBootTest
@ActiveProfiles("test")
class Phase12StructuredLoggingTest {

    @Autowired
    private Environment environment;

    @Test
    void consoleLoggingUsesStructuredJsonEncoder() {
        assertThat(environment.getProperty("logging.structured.format.console")).isEqualTo("ecs");

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        List<String> appenders = new ArrayList<>();
        boolean structuredEncoderFound = false;

        for (ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders();
            while (iterator.hasNext()) {
                Appender<ILoggingEvent> appender = iterator.next();
                Encoder<?> encoder = appender instanceof ConsoleAppender<?>
                        ? ((ConsoleAppender<?>) appender).getEncoder()
                        : null;
                String encoderName = encoder == null ? "" : encoder.getClass().getSimpleName();
                appenders.add(appender.getName() + "=" + encoderName);
                if ("StructuredLogEncoder".equals(encoderName)) {
                    structuredEncoderFound = true;
                }
            }
        }

        assertThat(structuredEncoderFound).as("appenders: %s", appenders).isTrue();
    }
}
