package com.civicsense.ai;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Selects exactly one AI provider from environment configuration. */
@Configuration
public class AiProviderConfig {

    @Bean
    AiVisionClient aiVisionClient(
            Environment environment,
            ObjectMapper mapper,
            @Value("${civicsense.ai.provider:stub}") String provider,
            @Value("${civicsense.ai.base-url:}") String baseUrl,
            @Value("${civicsense.ai.api-key:}") String apiKey,
            @Value("${civicsense.ai.model:gpt-4o-mini}") String model,
            @Value("${civicsense.ai.timeout-ms:20000}") long timeoutMs) {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if ("stub".equalsIgnoreCase(provider)) {
            if (prod) {
                throw new IllegalStateException("The stub AI provider is not permitted in prod");
            }
            return new StubVisionClient();
        }
        if ("openai-compatible".equalsIgnoreCase(provider)) {
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "CIVICSENSE_AI_BASE_URL and CIVICSENSE_AI_API_KEY are required for the AI provider");
            }
            return new OpenAiCompatibleVisionClient(baseUrl, apiKey, model,
                    Duration.ofMillis(timeoutMs), mapper);
        }
        throw new IllegalStateException("Unsupported AI provider: " + provider);
    }
}
