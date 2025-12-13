package com.gitlab.mirror.server.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Unified API Response
 *
 * @author GitLab Mirror Team
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private Boolean success;
    private T data;
    private String message;
    private ErrorInfo error;

    /**
     * Error information
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private String message;
        private String details;

        public ErrorInfo(String code, String message, String details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }

        public ErrorInfo(String code, String message) {
            this(code, message, null);
        }
    }

    /**
     * Success response
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage("Success");
        return response;
    }

    /**
     * Success response with custom message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        response.setMessage(message);
        return response;
    }

    /**
     * Success response without data
     */
    public static ApiResponse<Void> success() {
        return success(null, "Success");
    }

    /**
     * Error response
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(new ErrorInfo(code, message));
        return response;
    }

    /**
     * Error response with details
     */
    public static <T> ApiResponse<T> error(String code, String message, String details) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setError(new ErrorInfo(code, message, details));
        return response;
    }
}
