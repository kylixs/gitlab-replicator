package com.gitlab.mirror.server.service.auth.exception;

import lombok.Getter;

/**
 * Account Locked Exception
 * <p>
 * Thrown when account is locked due to too many failed login attempts
 *
 * @author GitLab Mirror Team
 */
@Getter
public class AccountLockedException extends RuntimeException {

    /**
     * Lockout duration in seconds
     */
    private final int lockoutSeconds;

    /**
     * Number of failed attempts
     */
    private final int failureCount;

    public AccountLockedException(int lockoutSeconds, int failureCount) {
        super(String.format("账户已锁定，请在 %d 秒后重试（失败次数：%d）", lockoutSeconds, failureCount));
        this.lockoutSeconds = lockoutSeconds;
        this.failureCount = failureCount;
    }
}
