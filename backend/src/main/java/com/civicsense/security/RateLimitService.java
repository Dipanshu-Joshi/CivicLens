package com.civicsense.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.civicsense.auth.exception.AuthExceptions.RateLimitExceededException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * In-memory Bucket4j rate limiter for auth and API endpoints. Single-instance
 * only; a Redis-backed bucket is required before horizontal scaling (documented
 * in ARCHITECTURE.md).
 */
@Service
public class RateLimitService {

    private final int loginCapacity;
    private final Duration loginWindow;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${civicsense.auth.login-rate-limit}") int loginCapacity,
            @Value("${civicsense.auth.login-rate-window-minutes}") long loginWindowMinutes) {
        this.loginCapacity = loginCapacity;
        this.loginWindow = Duration.ofMinutes(loginWindowMinutes);
    }

    /** Throws RateLimitExceededException (mapped to HTTP 429) when exhausted. */
    public void consume(String key) {
        if (!tryConsume(key, loginCapacity, loginWindow)) {
            throw new RateLimitExceededException(loginWindow.toSeconds());
        }
    }

    /** Returns true when one token was consumed; false when the bucket is empty. */
    public boolean tryConsume(String key, int capacity, Duration window) {
        pruneIfOversized();
        String bucketKey = capacity + ":" + window.toSeconds() + ":" + key;
        Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> newBucket(capacity, window));
        lastSeen.put(bucketKey, System.currentTimeMillis());
        return bucket.tryConsume(1);
    }

    public long retryAfterSeconds() {
        return loginWindow.toSeconds();
    }

    public long retryAfterSeconds(Duration window) {
        return window.toSeconds();
    }

    private Bucket newBucket(int capacity, Duration window) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, window)))
                .build();
    }

    /** Prevents unbounded growth from arbitrary IP keys. */
    private void pruneIfOversized() {
        if (buckets.size() < 10_000) {
            return;
        }
        long cutoff = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
        lastSeen.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        buckets.keySet().removeIf(key -> !lastSeen.containsKey(key));
    }
}
