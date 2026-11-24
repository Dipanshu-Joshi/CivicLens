package com.civicsense.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3-compatible implementation; works with Supabase Storage, MinIO, R2, and AWS S3. */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(
            S3Client client,
            S3Presigner presigner,
            @Value("${civicsense.storage.bucket}") String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public PresignedUrl presignPut(String key, String contentType, Duration ttl) {
        PresignedPutObjectRequest signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build());
        return toPresignedUrl(signed.url().toString(), signed.expiration(), signed.signedHeaders());
    }

    @Override
    public PresignedUrl presignGet(String key, Duration ttl) {
        PresignedGetObjectRequest signed = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(builder -> builder.bucket(bucket).key(key))
                .build());
        return toPresignedUrl(signed.url().toString(), signed.expiration(), signed.signedHeaders());
    }

    @Override
    public StoredObject get(String key) {
        try {
            ResponseBytes<software.amazon.awssdk.services.s3.model.GetObjectResponse> bytes =
                    client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                            ResponseTransformer.toBytes());
            return new StoredObject(bytes.asByteArray(),
                    bytes.response().contentType(), bytes.response().contentLength());
        } catch (NoSuchKeyException exception) {
            throw new StorageObjectNotFoundException(key);
        } catch (RuntimeException exception) {
            throw new StorageUnavailableException("Unable to read object from storage: " + key, exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException exception) {
            throw new StorageUnavailableException("Unable to delete object from storage: " + key, exception);
        }
    }

    private static PresignedUrl toPresignedUrl(
            String url, Instant expiresAt, Map<String, java.util.List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.get(0));
            }
        });
        return new PresignedUrl(url, expiresAt, Map.copyOf(headers));
    }
}
