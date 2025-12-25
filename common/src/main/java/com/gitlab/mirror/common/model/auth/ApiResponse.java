package com.gitlab.mirror.common.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Response Wrapper
 * <p>
 * Generic response wrapper for all API endpoints
 *
 * @param <T> Data type
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * Success flag
     */
    private boolean success;

    /**
     * Response data (if successful)
     */
    private T data;

    /**
     * Error information (if failed)
     */
    private ApiError error;

    /**
     * Create success response
     *
     * @param data Response data
     * @param <T>  Data type
     * @return Success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * Create success response without data
     *
     * @param <T> Data type
     * @return Success response
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .build();
    }

    /**
     * Create error response
     *
     * @param code    Error code
     * @param message Error message
     * @param <T>     Data type
     * @return Error response
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Create account locked error response
     *
     * @param retryAfter      Retry after seconds
     * @param failedAttempts  Failed attempts count
     * @param <T>             Data type
     * @return Account locked error response
     */
    public static <T> ApiResponse<T> accountLocked(int retryAfter, int failedAttempts) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder()
                        .code("ACCOUNT_LOCKED")
                        .message(String.format("账户已锁定，请在 %d 秒后重试", retryAfter))
                        .retryAfter(retryAfter)
                        .failedAttempts(failedAttempts)
                        .build())
                .build();
    }

    /**
     * Create rate limit error response
     *
     * @param retryAfter Retry after seconds
     * @param <T>        Data type
     * @return Rate limit error response
     */
    public static <T> ApiResponse<T> rateLimited(int retryAfter) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ApiError.builder()
                        .code("TOO_MANY_REQUESTS")
                        .message(String.format("请求过于频繁，请在 %d 秒后重试", retryAfter))
                        .retryAfter(retryAfter)
                        .build())
                .build();
    }
}
