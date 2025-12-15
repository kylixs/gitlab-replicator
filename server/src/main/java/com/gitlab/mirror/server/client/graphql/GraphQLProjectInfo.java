package com.gitlab.mirror.server.client.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * GraphQL项目信息DTO - 用于批量查询
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLProjectInfo {

    private String id;  // gid://gitlab/Project/1
    private String fullPath;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastActivityAt;

    private Repository repository;
    private Statistics statistics;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private String rootRef;  // 默认分支名
        private Tree tree;
        private java.util.List<String> branchNames;  // 分支名称列表

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Tree {
            private LastCommit lastCommit;

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class LastCommit {
                private String sha;
                private OffsetDateTime committedDate;
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statistics {
        private Double commitCount;  // GitLab返回float
        private Double repositorySize;
        private Double storageSize;
    }

    /**
     * 提取GitLab项目ID数字部分
     * gid://gitlab/Project/1 -> 1
     */
    public Long getProjectId() {
        if (id == null) return null;
        String[] parts = id.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    /**
     * 获取最后commit SHA
     */
    public String getLastCommitSha() {
        if (repository == null || repository.getTree() == null
                || repository.getTree().getLastCommit() == null) {
            return null;
        }
        return repository.getTree().getLastCommit().getSha();
    }

    /**
     * 获取最后commit时间
     */
    public OffsetDateTime getLastCommitDate() {
        if (repository == null || repository.getTree() == null
                || repository.getTree().getLastCommit() == null) {
            return null;
        }
        return repository.getTree().getLastCommit().getCommittedDate();
    }

    /**
     * 获取累计commit数量
     */
    public Integer getCommitCount() {
        if (statistics == null || statistics.getCommitCount() == null) {
            return null;
        }
        return statistics.getCommitCount().intValue();
    }

    /**
     * 获取仓库大小（字节）
     */
    public Long getRepositorySize() {
        if (statistics == null || statistics.getRepositorySize() == null) {
            return null;
        }
        return statistics.getRepositorySize().longValue();
    }

    /**
     * 获取分支数量
     */
    public Integer getBranchCount() {
        if (repository == null || repository.getBranchNames() == null) {
            return null;
        }
        return repository.getBranchNames().size();
    }
}
