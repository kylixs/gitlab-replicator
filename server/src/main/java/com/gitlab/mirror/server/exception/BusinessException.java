package com.gitlab.mirror.server.exception;

/**
 * Business Exception
 *
 * @author GitLab Mirror Team
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
