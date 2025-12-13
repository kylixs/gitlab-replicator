package com.gitlab.mirror.server.api.exception;

/**
 * Resource Not Found Exception
 *
 * @author GitLab Mirror Team
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(String.format("%s not found: %s", resourceType, identifier));
    }
}
