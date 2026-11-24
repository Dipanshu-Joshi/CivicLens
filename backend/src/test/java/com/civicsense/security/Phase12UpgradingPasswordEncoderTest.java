package com.civicsense.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class Phase12UpgradingPasswordEncoderTest {

    private final UpgradingPasswordEncoder encoder =
            new UpgradingPasswordEncoder(4, 16, 32, 1, 8192, 1);

    @Test
    void newHashesAreArgon2idAndVerifySuccessfully() {
        String hash = encoder.encode("Str0ng!Passw0rd");

        assertThat(hash).startsWith(UpgradingPasswordEncoder.ARGON2_ID_PREFIX);
        assertThat(encoder.matches("Str0ng!Passw0rd", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
        assertThat(encoder.upgradeRequired(hash)).isFalse();
    }

    @Test
    void legacyBcryptHashesStillVerifyAndAreMarkedForUpgrade() {
        String legacyHash = new BCryptPasswordEncoder(4).encode("Str0ng!Passw0rd");

        assertThat(encoder.matches("Str0ng!Passw0rd", legacyHash)).isTrue();
        assertThat(encoder.matches("Str0ng!Passw0rd", "{bcrypt}" + legacyHash)).isTrue();
        assertThat(encoder.matches("wrong-password", legacyHash)).isFalse();
        assertThat(encoder.upgradeRequired(legacyHash)).isTrue();
    }
}
