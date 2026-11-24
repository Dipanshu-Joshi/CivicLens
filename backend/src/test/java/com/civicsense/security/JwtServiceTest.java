package com.civicsense.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.civicsense.auth.exception.AuthExceptions.InvalidTokenException;
import com.civicsense.domain.Role;
import com.civicsense.domain.VerificationStatus;

import io.jsonwebtoken.Claims;

class JwtServiceTest {

    private static final String SECRET = "test-only-secret-key-that-is-at-least-32-characters-long";

    @Test
    void generatesAndParsesAccessTokenClaims() {
        Instant now = Instant.now();
        JwtService service = new JwtService(SECRET, 15, Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        String token = service.generateAccessToken(new AuthenticatedUser(
                userId, "user@example.com", Role.DEPARTMENT_ADMIN, deptId,
                VerificationStatus.PENDING_VERIFICATION));

        Claims claims = service.parse(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("DEPARTMENT_ADMIN");
        assertThat(claims.get(JwtService.CLAIM_DEPARTMENT_ID, String.class)).isEqualTo(deptId.toString());
        assertThat(service.principalFrom(claims).departmentVerificationStatus()).isNull();
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsTamperedAndExpiredTokens() {
        Instant now = Instant.now();
        JwtService service = new JwtService(SECRET, 15, Clock.fixed(now, ZoneOffset.UTC));
        String token = service.generateAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "user@example.com", Role.CITIZEN, null, null));

        assertThatThrownBy(() -> service.parse(token + "tampered"))
                .isInstanceOf(InvalidTokenException.class);

        // jjwt validates expiry against the real clock, so a token minted an
        // hour in the past is already expired when parsed.
        JwtService staleIssuer = new JwtService(SECRET, 15,
                Clock.fixed(now.minus(Duration.ofHours(1)), ZoneOffset.UTC));
        String expired = staleIssuer.generateAccessToken(new AuthenticatedUser(
                UUID.randomUUID(), "user@example.com", Role.CITIZEN, null, null));
        assertThatThrownBy(() -> service.parse(expired)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesShortSecrets() {
        assertThatThrownBy(() -> new JwtService("too-short", 15, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
