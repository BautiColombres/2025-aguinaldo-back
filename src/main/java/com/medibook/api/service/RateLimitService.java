package com.medibook.api.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared, bounded per-client token-bucket rate limiter.
 *
 * <p>Backed by Bucket4j. Buckets are held in a size-bounded LRU cache so the map
 * cannot grow without limit (replacing the previous unbounded, never-evicted
 * {@code ConcurrentHashMap} in {@code ExternalController}). The cache is
 * synchronized; the limiter is invoked once per request so contention is low.
 */
public class RateLimitService {

    private final long capacity;
    private final Duration refillPeriod;
    private final int maxBuckets;
    private final Map<String, Bucket> buckets;

    /**
     * @param capacity      max tokens (requests) per refill period per client key
     * @param refillMinutes period after which the bucket fully refills, in minutes
     * @param maxBuckets    hard cap on distinct client keys held in memory (LRU eviction)
     */
    public RateLimitService(long capacity, long refillMinutes, int maxBuckets) {
        this.capacity = capacity;
        this.refillPeriod = Duration.ofMinutes(refillMinutes);
        this.maxBuckets = maxBuckets;
        this.buckets = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > RateLimitService.this.maxBuckets;
            }
        });
    }

    /**
     * Attempts to consume a single token for the given client key.
     *
     * @return {@code true} if the request is within the limit, {@code false} if it
     *         should be rejected (HTTP 429).
     */
    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
        return bucket.tryConsume(1);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }

    /** Current number of tracked client buckets (for tests / monitoring). */
    public int cacheSize() {
        return buckets.size();
    }
}
