package com.bank.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard: tracks consecutive failed logins per client IP and
 * blocks that IP for a cool-off window once a threshold is exceeded.
 *
 * <p>Keyed by IP (not username) so an attacker cannot lock a victim out of their own
 * account by guessing their password. State is per-instance; a multi-node deployment
 * would back this with a shared store (e.g. Redis) — the {@code LoginAttemptService}
 * interface would stay the same.
 */
@Component
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration blockDuration;
    private final Map<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${banking.security.login.max-attempts:5}") int maxAttempts,
            @Value("${banking.security.login.block-minutes:15}") long blockMinutes) {
        this.maxAttempts = maxAttempts;
        this.blockDuration = Duration.ofMinutes(blockMinutes);
    }

    /** A successful authentication clears the counter for that IP. */
    public void onSuccess(String ip) {
        attemptsByIp.remove(ip);
    }

    /** Record a failed authentication; the IP is blocked once it reaches the threshold. */
    public void onFailure(String ip) {
        attemptsByIp.compute(ip, (key, existing) -> {
            Attempts attempts = existing == null ? new Attempts() : existing;
            attempts.count++;
            if (attempts.count >= maxAttempts) {
                attempts.blockedUntil = Instant.now().plus(blockDuration);
            }
            return attempts;
        });
    }

    public boolean isBlocked(String ip) {
        Attempts attempts = attemptsByIp.get(ip);
        if (attempts == null || attempts.blockedUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(attempts.blockedUntil)) {
            attemptsByIp.remove(ip); // cool-off elapsed
            return false;
        }
        return true;
    }

    /** Clears all tracked state (used to isolate tests). */
    public void clear() {
        attemptsByIp.clear();
    }

    private static final class Attempts {
        private int count;
        private Instant blockedUntil;
    }
}
