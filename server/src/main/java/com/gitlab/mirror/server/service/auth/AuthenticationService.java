package com.gitlab.mirror.server.service.auth;

import com.gitlab.mirror.server.entity.AuthToken;
import com.gitlab.mirror.server.entity.User;
import com.gitlab.mirror.server.mapper.AuthTokenMapper;
import com.gitlab.mirror.server.mapper.UserMapper;
import com.gitlab.mirror.server.service.auth.exception.AccountLockedException;
import com.gitlab.mirror.server.service.auth.exception.AuthenticationException;
import com.gitlab.mirror.server.service.auth.model.ChallengeInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentication Service
 * <p>
 * Core SCRAM-SHA-256 authentication service with:
 * - Challenge-response authentication
 * - Brute-force protection integration
 * - Audit logging
 * - Token management
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserMapper userMapper;
    private final AuthTokenMapper authTokenMapper;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final LoginAuditService loginAuditService;

    // In-memory challenge storage with 30-second expiration
    private final Map<String, ChallengeInfo> challengeStore = new ConcurrentHashMap<>();

    /**
     * Generate authentication challenge
     *
     * @param username Username
     * @return Challenge info (salt, iterations, challenge code)
     * @throws AuthenticationException If user not found
     */
    public ChallengeInfo generateChallenge(String username) {
        // Query user
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            log.debug("Challenge generation failed - user not found: {}", username);
            throw new AuthenticationException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            log.debug("Challenge generation failed - user disabled: {}", username);
            throw new AuthenticationException("用户已禁用");
        }

        // Generate challenge
        String challenge = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(30);

        ChallengeInfo challengeInfo = new ChallengeInfo();
        challengeInfo.setChallenge(challenge);
        challengeInfo.setSalt(user.getSalt());
        challengeInfo.setIterations(user.getIterations());
        challengeInfo.setExpiresAt(expiresAt);
        challengeInfo.setUsed(false);

        // Store challenge
        challengeStore.put(challenge, challengeInfo);

        log.debug("Challenge generated for user: {}", username);
        return challengeInfo;
    }

    /**
     * Login with SCRAM authentication
     *
     * @param username    Username
     * @param challenge   Challenge code
     * @param clientProof Client proof
     * @param ip          Client IP address
     * @param userAgent   User agent
     * @return Authentication token
     * @throws AccountLockedException  If account is locked
     * @throws AuthenticationException If authentication fails
     */
    @Transactional
    public String login(String username, String challenge, String clientProof, String ip, String userAgent) {
        // Check brute-force protection
        int lockoutSeconds = bruteForceProtectionService.checkLoginAllowed(username, ip);
        if (lockoutSeconds > 0) {
            int failureCount = bruteForceProtectionService.getFailureCount(username);
            log.warn("Login blocked by rate limit - Username: {}, IP: {}, Lockout: {}s, Failures: {}",
                    username, ip, lockoutSeconds, failureCount);

            loginAuditService.recordRateLimited(username, ip, userAgent);
            throw new AccountLockedException(lockoutSeconds, failureCount);
        }

        // Validate challenge
        ChallengeInfo challengeInfo = challengeStore.get(challenge);
        if (challengeInfo == null) {
            log.debug("Login failed - invalid challenge: {}", challenge);
            recordFailureAndAudit(username, ip, userAgent, "无效的挑战码");
            throw new AuthenticationException("用户名或密码错误");
        }

        if (challengeInfo.isUsed()) {
            log.debug("Login failed - challenge already used: {}", challenge);
            recordFailureAndAudit(username, ip, userAgent, "挑战码已使用");
            throw new AuthenticationException("用户名或密码错误");
        }

        if (LocalDateTime.now().isAfter(challengeInfo.getExpiresAt())) {
            log.debug("Login failed - challenge expired: {}", challenge);
            challengeStore.remove(challenge);
            recordFailureAndAudit(username, ip, userAgent, "挑战码已过期");
            throw new AuthenticationException("用户名或密码错误");
        }

        // Mark challenge as used
        challengeInfo.setUsed(true);
        challengeStore.remove(challenge);

        // Query user
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            log.debug("Login failed - user not found: {}", username);
            recordFailureAndAudit(username, ip, userAgent, "用户不存在");
            throw new AuthenticationException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            log.debug("Login failed - user disabled: {}", username);
            recordFailureAndAudit(username, ip, userAgent, "用户已禁用");
            throw new AuthenticationException("用户已禁用");
        }

        // Verify SCRAM proof
        boolean valid = ScramUtils.verifyClientProof(username, challenge, clientProof, user.getStoredKey());
        if (!valid) {
            log.debug("Login failed - invalid credentials: {}", username);
            recordFailureAndAudit(username, ip, userAgent, "密码错误");
            throw new AuthenticationException("用户名或密码错误");
        }

        // Authentication successful
        log.info("Login successful - Username: {}, IP: {}", username, ip);

        // Reset failure count
        bruteForceProtectionService.resetFailureCount(username, ip);

        // Generate token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);

        AuthToken authToken = new AuthToken();
        authToken.setUserId(user.getId());
        authToken.setToken(token);
        authToken.setExpiresAt(expiresAt);
        authTokenMapper.insert(authToken);

        // Record audit log
        loginAuditService.recordLoginSuccess(username, ip, userAgent);

        return token;
    }

    /**
     * Validate token
     *
     * @param token Authentication token
     * @return User object if valid, null if invalid
     */
    public User validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        AuthToken authToken = authTokenMapper.selectByToken(token);
        if (authToken == null) {
            return null;
        }

        // Check expiration
        if (LocalDateTime.now().isAfter(authToken.getExpiresAt())) {
            log.debug("Token expired: {}", token);
            return null;
        }

        // Update last used time
        authTokenMapper.updateLastUsedAt(token, LocalDateTime.now());

        // Get user
        User user = userMapper.selectById(authToken.getUserId());
        if (user == null || !user.getEnabled()) {
            return null;
        }

        return user;
    }

    /**
     * Logout (delete token)
     *
     * @param token Authentication token
     */
    @Transactional
    public void logout(String token) {
        AuthToken authToken = authTokenMapper.selectByToken(token);
        if (authToken != null) {
            authTokenMapper.deleteById(authToken.getId());
            log.debug("Token deleted: {}", token);
        }
    }

    /**
     * Record login failure and audit
     */
    private void recordFailureAndAudit(String username, String ip, String userAgent, String reason) {
        bruteForceProtectionService.recordLoginFailure(username, ip);

        // Check if account is now locked
        int lockoutSeconds = bruteForceProtectionService.checkLoginAllowed(username, ip);
        if (lockoutSeconds > 0) {
            loginAuditService.recordAccountLocked(username, ip, userAgent);
        } else {
            loginAuditService.recordLoginFailure(username, ip, userAgent, reason);
        }
    }

    /**
     * Clean up expired challenges (every minute)
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredChallenges() {
        LocalDateTime now = LocalDateTime.now();
        int removed = 0;

        var iterator = challengeStore.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now.isAfter(entry.getValue().getExpiresAt())) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("Cleaned up {} expired challenges", removed);
        }
    }

    /**
     * Clean up expired tokens (every hour)
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        int deleted = authTokenMapper.deleteExpiredTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired tokens", deleted);
        }
    }
}
