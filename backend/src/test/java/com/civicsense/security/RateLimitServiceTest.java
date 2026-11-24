package com.civicsense.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.civicsense.auth.exception.AuthExceptions.RateLimitExceededException;

class RateLimitServiceTest {

    @Test
    void blocksRequestsBeyondConfiguredCapacity() {
        RateLimitService service = new RateLimitService(2, 15);
        String key = "203.0.113.10";

        assertThatCode(() -> {
            service.consume(key);
            service.consume(key);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> service.consume(key))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(service.retryAfterSeconds()).isEqualTo(900);
    }
}
