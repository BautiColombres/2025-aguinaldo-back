package com.medibook.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared, bounded token-bucket rate limiter used by the auth filter
 * and by ExternalController (replacing its unbounded in-memory counter map).
 */
class RateLimitServiceTest {

    @Test
    void allowsUpToCapacityThenBlocks() {
        RateLimitService service = new RateLimitService(3, 1, 1000);
        assertTrue(service.tryConsume("client-a"));
        assertTrue(service.tryConsume("client-a"));
        assertTrue(service.tryConsume("client-a"));
        assertFalse(service.tryConsume("client-a"), "4th request over capacity 3 must be blocked");
    }

    @Test
    void differentKeysAreIndependent() {
        RateLimitService service = new RateLimitService(1, 1, 1000);
        assertTrue(service.tryConsume("client-a"));
        assertTrue(service.tryConsume("client-b"));
    }

    @Test
    void bucketCacheIsBoundedAndEvicts() {
        int maxBuckets = 50;
        RateLimitService service = new RateLimitService(10, 1, maxBuckets);
        for (int i = 0; i < 10_000; i++) {
            service.tryConsume("client-" + i);
        }
        assertTrue(service.cacheSize() <= maxBuckets,
                "bucket cache must stay bounded (size=" + service.cacheSize()
                        + ", max=" + maxBuckets + ")");
    }
}
