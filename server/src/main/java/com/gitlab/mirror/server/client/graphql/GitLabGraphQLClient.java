package com.gitlab.mirror.server.client.graphql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab GraphQL API 客户端
 * <p>
 * 用于批量查询项目基础信息，性能优于REST API
 */
@Slf4j
@Component
public class GitLabGraphQLClient {

    private final ObjectMapper objectMapper;

    // GraphQL查询模板 - 批量查询项目基础信息
    private static final String BATCH_QUERY_TEMPLATE = """
            query($ids: [ID!]) {
              projects(ids: $ids) {
                nodes {
                  id
                  fullPath
                  createdAt
                  lastActivityAt
                  repository {
                    rootRef
                    tree {
                      lastCommit {
                        sha
                        committedDate
                      }
                    }
                    branchNames
                  }
                  statistics {
                    commitCount
                    repositorySize
                    storageSize
                  }
                }
              }
            }
            """;

    public GitLabGraphQLClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 批量查询项目基础信息（使用项目ID）
     *
     * @param projectIds 项目ID列表（数字ID，非gid）
     * @param client GitLab API客户端
     * @return 项目信息列表
     */
    public List<GraphQLProjectInfo> batchQueryProjects(List<Long> projectIds, RetryableGitLabClient client) {
        if (projectIds == null || projectIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为GitLab GraphQL ID格式
        List<String> gids = new ArrayList<>();
        for (Long id : projectIds) {
            gids.add("gid://gitlab/Project/" + id);
        }

        // 构建GraphQL请求
        Map<String, Object> variables = new HashMap<>();
        variables.put("ids", gids);
        GraphQLRequest request = new GraphQLRequest(BATCH_QUERY_TEMPLATE, variables);

        try {
            // 发送请求
            long startTime = System.currentTimeMillis();
            log.info("[GraphQL] Batch querying {} projects", projectIds.size());

            // 使用RetryableGitLabClient的post方法
            String responseBody = client.post("/api/graphql", request, String.class);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[API-PERF] POST /api/graphql (batch {} projects) - {}ms",
                    projectIds.size(), duration);

            // 解析响应
            GraphQLResponse<Map<String, Object>> graphQLResponse = objectMapper.readValue(
                    responseBody,
                    new TypeReference<GraphQLResponse<Map<String, Object>>>() {
                    });

            if (graphQLResponse.hasErrors()) {
                log.error("[GraphQL] Query failed: {}", graphQLResponse.getErrorMessage());
                throw new RuntimeException("GraphQL query failed: " + graphQLResponse.getErrorMessage());
            }

            // 提取projects节点
            Map<String, Object> data = graphQLResponse.getData();
            if (data == null || !data.containsKey("projects")) {
                log.warn("[GraphQL] No projects data in response");
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> projects = (Map<String, Object>) data.get("projects");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) projects.get("nodes");

            if (nodes == null) {
                return new ArrayList<>();
            }

            // 转换为GraphQLProjectInfo对象
            List<GraphQLProjectInfo> result = new ArrayList<>();
            for (Map<String, Object> node : nodes) {
                String json = objectMapper.writeValueAsString(node);
                GraphQLProjectInfo info = objectMapper.readValue(json, GraphQLProjectInfo.class);
                result.add(info);
            }

            log.info("[GraphQL] Successfully queried {} projects", result.size());
            return result;

        } catch (Exception e) {
            log.error("[GraphQL] Batch query failed for {} projects: {}",
                    projectIds.size(), e.getMessage(), e);
            throw new RuntimeException("GraphQL batch query failed", e);
        }
    }

    /**
     * 分批查询项目（避免单次查询项目过多）
     *
     * @param projectIds 项目ID列表
     * @param batchSize  每批数量（建议20-50）
     * @param client GitLab API客户端
     * @return 所有项目信息
     */
    public List<GraphQLProjectInfo> batchQueryProjectsInChunks(List<Long> projectIds, int batchSize,
            RetryableGitLabClient client) {
        if (projectIds == null || projectIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<GraphQLProjectInfo> allProjects = new ArrayList<>();
        int totalBatches = (int) Math.ceil((double) projectIds.size() / batchSize);

        log.info("[GraphQL] Querying {} projects in {} batches (size={})",
                projectIds.size(), totalBatches, batchSize);

        for (int i = 0; i < projectIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, projectIds.size());
            List<Long> batch = projectIds.subList(i, end);

            int batchNum = (i / batchSize) + 1;
            log.info("[GraphQL] Processing batch {}/{} ({} projects)",
                    batchNum, totalBatches, batch.size());

            List<GraphQLProjectInfo> batchResult = batchQueryProjects(batch, client);
            allProjects.addAll(batchResult);
        }

        log.info("[GraphQL] Total projects queried: {}", allProjects.size());
        return allProjects;
    }
}
