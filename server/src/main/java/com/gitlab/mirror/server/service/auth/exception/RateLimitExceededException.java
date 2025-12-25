package com.gitlab.mirror.server.service.auth.exception;

import lombok.Getter;

/**
 * Rate Limit Exceeded Exception
 * <p>
 * Thrown when rate limit is exceeded
 *
 * @author GitLab Mirror Team
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /**
     * Retry after seconds
     */
    private final int retryAfter;

    public RateLimitExceededException(int retryAfter) {
        super(String.format("请求过于频繁，请在 %d 秒后重试", retryAfter));
        this.retryAfter = retryAfter;
    }
}
