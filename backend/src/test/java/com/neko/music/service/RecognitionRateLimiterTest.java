package com.neko.music.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognitionRateLimiterTest {
    @Test
    void limitsBurstAndRefills() {
        RecognitionRateLimiter limiter = new RecognitionRateLimiter();
        long now = 1_000_000_000L;
        assertTrue(limiter.tryAcquire("127.0.0.1", 2, now).allowed());
        assertTrue(limiter.tryAcquire("127.0.0.1", 2, now).allowed());
        assertFalse(limiter.tryAcquire("127.0.0.1", 2, now).allowed());
        assertTrue(limiter.tryAcquire("127.0.0.1", 2, now + 31_000_000_000L).allowed());
    }
}
