package com.neko.music.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight per-process token bucket for the CPU-heavy recognition endpoint. */
public final class RecognitionRateLimiter {
    private static final long STALE_AFTER_NANOS = Duration.ofHours(2).toNanos();

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    public Decision tryAcquire(String clientKey, int requestsPerMinute) {
        return tryAcquire(clientKey, requestsPerMinute, System.nanoTime());
    }

    Decision tryAcquire(String clientKey, int requestsPerMinute, long nowNanos) {
        int capacity = Math.max(1, requestsPerMinute);
        String key = clientKey == null || clientKey.isBlank() ? "unknown" : clientKey;
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity, nowNanos));
        Decision decision = bucket.tryAcquire(capacity, nowNanos);
        if ((requests.incrementAndGet() & 0x3ffL) == 0L) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isStale(nowNanos));
        }
        return decision;
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;
        private long lastSeenNanos;

        private Bucket(int capacity, long nowNanos) {
            tokens = capacity;
            lastRefillNanos = nowNanos;
            lastSeenNanos = nowNanos;
        }

        private synchronized Decision tryAcquire(int capacity, long nowNanos) {
            if (nowNanos < lastRefillNanos) {
                lastRefillNanos = nowNanos;
            }
            double tokensPerNano = capacity / (double) Duration.ofMinutes(1).toNanos();
            tokens = Math.min(capacity, tokens + (nowNanos - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = nowNanos;
            lastSeenNanos = nowNanos;
            if (tokens >= 1d) {
                tokens -= 1d;
                return new Decision(true, 0);
            }
            long waitNanos = (long) Math.ceil((1d - tokens) / tokensPerNano);
            long retrySeconds = Math.max(1L, TimeUnitHelper.ceilSeconds(waitNanos));
            return new Decision(false, retrySeconds);
        }

        private synchronized boolean isStale(long nowNanos) {
            return nowNanos - lastSeenNanos > STALE_AFTER_NANOS;
        }
    }

    private static final class TimeUnitHelper {
        private static long ceilSeconds(long nanos) {
            long oneSecond = Duration.ofSeconds(1).toNanos();
            return (nanos + oneSecond - 1) / oneSecond;
        }
    }
}
