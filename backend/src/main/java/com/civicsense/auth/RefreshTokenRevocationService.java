package com.civicsense.auth;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.civicsense.identity.RefreshToken;
import com.civicsense.identity.RefreshTokenRepository;

/**
 * Revocation must commit even when the caller aborts its transaction with a
 * 401 (token reuse is treated as theft). REQUIRES_NEW guarantees that.
 */
@Service
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public RefreshTokenRevocationService(
            RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.findAllByUserId(userId).forEach(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoke(RefreshToken token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(clock.instant());
        }
    }
}
