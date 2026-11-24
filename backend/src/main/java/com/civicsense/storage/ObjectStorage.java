package com.civicsense.storage;

import java.time.Duration;

/**
 * Provider-independent object storage abstraction. Supabase S3-compatible
 * storage is the initial implementation, but callers never depend on S3 types.
 */
public interface ObjectStorage {

    /** Creates a short-lived upload URL for a single object. */
    PresignedUrl presignPut(String key, String contentType, Duration ttl);

    /** Creates a short-lived read URL for a single object. */
    PresignedUrl presignGet(String key, Duration ttl);

    /**
     * Reads the full object. Used during confirmation to verify magic bytes,
     * dimensions, and checksum before an image is trusted.
     *
     * @throws StorageObjectNotFoundException when the key does not exist
     */
    StoredObject get(String key);

    /** Deletes an object. Missing objects are treated as success. */
    void delete(String key);
}
