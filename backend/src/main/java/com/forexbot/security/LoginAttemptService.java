package com.forexbot.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection: tracks failed login attempts per IP address.
 * After MAX_ATTEMPTS failures within BLOCK_DURATION, further attempts are blocked.
 * State is cleared on successful login or after the block window expires.
 *
 * Note: for multi-instance deployments, replace with a Redis-backed store.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS    = 5;
    private static final Duration BLOCK_WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Slot> cache = new ConcurrentHashMap<>();

    public void loginSucceeded(String ip) {
        cache.remove(ip);
    }

    public void loginFailed(String ip) {
        cache.compute(ip, (k, slot) -> {
            if (slot == null) slot = new Slot();
            slot.increment();
            return slot;
        });
    }

    public boolean isBlocked(String ip) {
        Slot slot = cache.get(ip);
        if (slot == null) return false;
        if (slot.count < MAX_ATTEMPTS) return false;
        if (Duration.between(slot.lastAttempt, Instant.now()).compareTo(BLOCK_WINDOW) > 0) {
            cache.remove(ip);   // block window has passed — reset
            return false;
        }
        return true;
    }

    private static class Slot {
        int count = 0;
        Instant lastAttempt = Instant.now();

        void increment() {
            count++;
            lastAttempt = Instant.now();
        }
    }
}
