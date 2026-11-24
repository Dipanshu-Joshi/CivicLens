package com.civicsense.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.civicsense.auth.exception.AuthExceptions.InvalidTokenException;
import com.civicsense.domain.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Issues and validates short-lived access tokens. Refresh tokens are not JWTs. */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_DEPARTMENT_ID = "deptId";
    public static final String CLAIM_EMAIL = "email";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(
            @Value("${civicsense.auth.jwt-secret}") String secret,
            @Value("${civicsense.auth.access-token-minutes}") long accessTokenMinutes,
            Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("civicsense.auth.jwt-secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.clock = clock;
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_DEPARTMENT_ID,
                        user.departmentId() == null ? null : user.departmentId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /** Parses and verifies signature + expiry. Throws InvalidTokenException on failure. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    /** Convenience for tests and internal callers. */
    public AuthenticatedUser principalFrom(Claims claims) {
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                parseNullableUuid(claims.get(CLAIM_DEPARTMENT_ID, String.class)),
                null // verification status is always refreshed from the database
        );
    }

    private UUID parseNullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
