package com.gitlab.mirror.server.client.graphql;

import lombok.Data;

import java.util.Map;

/**
 * GraphQL请求DTO
 */
@Data
public class GraphQLRequest {
    private String query;
    private Map<String, Object> variables;

    public GraphQLRequest(String query) {
        this.query = query;
    }

    public GraphQLRequest(String query, Map<String, Object> variables) {
        this.query = query;
        this.variables = variables;
    }
}
