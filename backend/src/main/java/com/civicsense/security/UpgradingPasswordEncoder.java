package com.civicsense.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Gradual BCrypt → Argon2id upgrade.
 *
 * <p>New and reset passwords are encoded as Argon2id with an explicit prefix.
 * Legacy BCrypt hashes (with or without the standard {@code {bcrypt}} prefix)
 * remain verifiable. After a successful login, {@link #upgradeRequired(String)}
 * tells the authentication service to re-encode the raw password, so no user is
 * forced to reset their password.</p>
 */
public final class UpgradingPasswordEncoder implements PasswordEncoder {

    public static final String ARGON2_ID_PREFIX = "{argon2id}";
    private static final String BCRYPT_PREFIX = "{bcrypt}";

    private final BCryptPasswordEncoder bcrypt;
    private final Argon2PasswordEncoder argon2;

    public UpgradingPasswordEncoder(
            int bcryptStrength,
            int saltLength,
            int hashLength,
            int parallelism,
            int memoryKb,
            int iterations) {
        this.bcrypt = new BCryptPasswordEncoder(bcryptStrength);
        this.argon2 = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKb, iterations);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return ARGON2_ID_PREFIX + argon2.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (encodedPassword.startsWith(ARGON2_ID_PREFIX)) {
            return argon2.matches(rawPassword, encodedPassword.substring(ARGON2_ID_PREFIX.length()));
        }
        if (encodedPassword.startsWith(BCRYPT_PREFIX)) {
            return bcrypt.matches(rawPassword, encodedPassword.substring(BCRYPT_PREFIX.length()));
        }
        return bcrypt.matches(rawPassword, encodedPassword);
    }

    /** True when a verified legacy hash should be re-encoded as Argon2id. */
    public boolean upgradeRequired(String encodedPassword) {
        return encodedPassword == null
                || !encodedPassword.startsWith(ARGON2_ID_PREFIX);
    }
}
