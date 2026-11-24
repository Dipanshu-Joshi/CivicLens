package com.civicsense.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.civicsense.security.UpgradingPasswordEncoder;

/** Shared application beans. Password hashing upgrades gradually to Argon2id. */
@Configuration
public class ApplicationBeansConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UpgradingPasswordEncoder passwordEncoder(
            @Value("${civicsense.auth.bcrypt-strength}") int bcryptStrength,
            @Value("${civicsense.auth.argon2-salt-length}") int argon2SaltLength,
            @Value("${civicsense.auth.argon2-hash-length}") int argon2HashLength,
            @Value("${civicsense.auth.argon2-parallelism}") int argon2Parallelism,
            @Value("${civicsense.auth.argon2-memory-kb}") int argon2MemoryKb,
            @Value("${civicsense.auth.argon2-iterations}") int argon2Iterations) {
        return new UpgradingPasswordEncoder(
                bcryptStrength,
                argon2SaltLength,
                argon2HashLength,
                argon2Parallelism,
                argon2MemoryKb,
                argon2Iterations);
    }
}
