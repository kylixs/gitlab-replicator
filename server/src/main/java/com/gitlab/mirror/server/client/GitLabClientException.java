package com.gitlab.mirror.server.client;

/**
 * GitLab Client Exception
 *
 * @author GitLab Mirror Team
 */
public class GitLabClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public GitLabClientException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public GitLabClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public GitLabClientException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
