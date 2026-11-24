package com.civicsense.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3-compatible client. Supabase Storage is used in initial development, but
 * the same bean works with MinIO, R2 or AWS S3 by changing .env only.
 */
@Configuration
public class StorageConfig {

    @Bean
    S3Client s3Client(
            @Value("${civicsense.storage.endpoint}") String endpoint,
            @Value("${civicsense.storage.region}") String region,
            @Value("${civicsense.storage.access-key}") String accessKey,
            @Value("${civicsense.storage.secret-key}") String secretKey,
            @Value("${civicsense.storage.path-style}") boolean pathStyle) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(
            @Value("${civicsense.storage.endpoint}") String endpoint,
            @Value("${civicsense.storage.region}") String region,
            @Value("${civicsense.storage.access-key}") String accessKey,
            @Value("${civicsense.storage.secret-key}") String secretKey,
            @Value("${civicsense.storage.path-style}") boolean pathStyle) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build())
                .build();
    }
}
