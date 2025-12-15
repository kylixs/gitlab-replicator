package com.gitlab.mirror.server.client.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * GraphQL响应DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLResponse<T> {
    private T data;
    private List<GraphQLError> errors;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphQLError {
        private String message;
        private List<Location> locations;
        private List<String> path;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Location {
            private int line;
            private int column;
        }
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public String getErrorMessage() {
        if (!hasErrors()) return null;
        StringBuilder sb = new StringBuilder();
        for (GraphQLError error : errors) {
            sb.append(error.getMessage()).append("; ");
        }
        return sb.toString();
    }
}
