package com.gitlab.mirror.server.service.auth.exception;

/**
 * Authentication Exception
 * <p>
 * Thrown when authentication fails
 *
 * @author GitLab Mirror Team
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
