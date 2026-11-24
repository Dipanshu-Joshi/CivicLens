package com.civicsense.storage;

import java.time.Instant;
import java.util.Map;

/** A short-lived capability URL returned to the frontend. */
public record PresignedUrl(String url, Instant expiresAt, Map<String, String> headers) {
}
