package com.gitlab.mirror.server.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.gitlab.mirror.server.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-Force Protection Service
 * <p>
 * Implements multi-layer brute-force attack protection:
 * - IP-level rate limiting
 * - Account-level rate limiting
 * - Exponential backoff lockout
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BruteForceProtectionService {

    private final AuthProperties authProperties;

    // Cache for IP-level failure counts
    private final Cache<String, AtomicInteger> ipFailureCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    // Cache for account-level failure counts
    private final Cache<String, AtomicInteger> accountFailureCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /**
     * Check if login is allowed for the given username and IP
     *
     * @param username Username
     * @param ip       Client IP address
     * @return Lockout duration in seconds (0 if login is allowed)
     */
    public int checkLoginAllowed(String username, String ip) {
        // Check IP-level rate limit
        int ipFailures = getFailureCount("ip:" + ip);
        if (ipFailures >= authProperties.getBruteForce().getMaxIpFailures()) {
            int lockoutSeconds = calculateExponentialBackoff(ipFailures);
            log.warn("IP {} exceeded failure limit: {} failures", ip, ipFailures);
            return lockoutSeconds;
        }

        // Check account-level rate limit
        int accountFailures = getFailureCount("user:" + username);
        if (accountFailures >= authProperties.getBruteForce().getMaxAccountFailures()) {
            int lockoutSeconds = calculateExponentialBackoff(accountFailures);
            log.warn("Account {} exceeded failure limit: {} failures", username, accountFailures);
            return lockoutSeconds;
        }

        return 0; // Login allowed
    }

    /**
     * Record a login failure
     *
     * @param username Username
     * @param ip       Client IP address
     */
    public void recordLoginFailure(String username, String ip) {
        // Increment IP failure count
        ipFailureCache.get("ip:" + ip, k -> new AtomicInteger(0)).incrementAndGet();

        // Increment account failure count
        accountFailureCache.get("user:" + username, k -> new AtomicInteger(0)).incrementAndGet();

        int ipFailures = getFailureCount("ip:" + ip);
        int accountFailures = getFailureCount("user:" + username);

        log.debug("Login failure recorded - Username: {}, IP: {}, IP failures: {}, Account failures: {}",
                username, ip, ipFailures, accountFailures);
    }

    /**
     * Reset failure counts after successful login
     *
     * @param username Username
     * @param ip       Client IP address
     */
    public void resetFailureCount(String username, String ip) {
        ipFailureCache.invalidate("ip:" + ip);
        accountFailureCache.invalidate("user:" + username);
        log.debug("Failure counts reset for username: {}, IP: {}", username, ip);
    }

    /**
     * Get failure count for a given key
     *
     * @param username Username
     * @return Failure count
     */
    public int getFailureCount(String username) {
        AtomicInteger count = accountFailureCache.getIfPresent("user:" + username);
        return count != null ? count.get() : 0;
    }

    /**
     * Calculate exponential backoff lockout duration
     * <p>
     * Formula: 2^(failCount - threshold) seconds
     * Maximum: configured max lockout duration
     *
     * @param failCount Failure count
     * @return Lockout duration in seconds
     */
    public int calculateExponentialBackoff(int failCount) {
        // Start exponential backoff after threshold
        int threshold = authProperties.getBruteForce().getMaxAccountFailures();
        if (failCount < threshold) {
            return 0;
        }

        // Calculate: 2^(failCount - threshold + 1)
        int exponent = failCount - threshold + 1;
        int lockoutSeconds = (int) Math.pow(2, exponent);

        // Cap at maximum lockout duration
        int maxLockout = authProperties.getBruteForce().getMaxLockoutSeconds();
        return Math.min(lockoutSeconds, maxLockout);
    }
}
