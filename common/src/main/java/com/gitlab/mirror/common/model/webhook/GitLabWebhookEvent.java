package com.gitlab.mirror.common.model.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GitLab Webhook Event DTO
 * <p>
 * Represents a GitLab webhook push event payload
 * https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html#push-events
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitLabWebhookEvent {

    /**
     * Event name (e.g., "push")
     */
    @JsonProperty("event_name")
    private String eventName;

    /**
     * Before commit SHA
     */
    private String before;

    /**
     * After commit SHA
     */
    private String after;

    /**
     * Git reference (e.g., "refs/heads/main")
     */
    private String ref;

    /**
     * Checkout SHA
     */
    @JsonProperty("checkout_sha")
    private String checkoutSha;

    /**
     * User ID
     */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * User name
     */
    @JsonProperty("user_name")
    private String userName;

    /**
     * User email
     */
    @JsonProperty("user_email")
    private String userEmail;

    /**
     * User avatar URL
     */
    @JsonProperty("user_avatar")
    private String userAvatar;

    /**
     * Project ID
     */
    @JsonProperty("project_id")
    private Long projectId;

    /**
     * Project details
     */
    private Project project;

    /**
     * Commits in this push
     */
    private List<Commit> commits;

    /**
     * Total commits count
     */
    @JsonProperty("total_commits_count")
    private Integer totalCommitsCount;

    /**
     * Repository details
     */
    private Repository repository;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        private Long id;
        private String name;
        private String description;

        @JsonProperty("web_url")
        private String webUrl;

        @JsonProperty("avatar_url")
        private String avatarUrl;

        @JsonProperty("git_ssh_url")
        private String gitSshUrl;

        @JsonProperty("git_http_url")
        private String gitHttpUrl;

        private String namespace;

        @JsonProperty("visibility_level")
        private Integer visibilityLevel;

        @JsonProperty("path_with_namespace")
        private String pathWithNamespace;

        @JsonProperty("default_branch")
        private String defaultBranch;

        @JsonProperty("homepage")
        private String homepage;

        @JsonProperty("url")
        private String url;

        @JsonProperty("ssh_url")
        private String sshUrl;

        @JsonProperty("http_url")
        private String httpUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Commit {
        private String id;
        private String message;
        private String title;
        private OffsetDateTime timestamp;
        private String url;
        private Author author;
        private List<String> added;
        private List<String> modified;
        private List<String> removed;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Author {
            private String name;
            private String email;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private String name;
        private String url;
        private String description;
        private String homepage;

        @JsonProperty("git_http_url")
        private String gitHttpUrl;

        @JsonProperty("git_ssh_url")
        private String gitSshUrl;

        @JsonProperty("visibility_level")
        private Integer visibilityLevel;
    }

    /**
     * Helper method to get branch name from ref
     */
    public String getBranchName() {
        if (ref == null) {
            return null;
        }
        // ref format: "refs/heads/main"
        return ref.replaceFirst("^refs/heads/", "");
    }

    /**
     * Helper method to check if this is a push to default branch
     */
    public boolean isDefaultBranchPush() {
        if (project == null || project.getDefaultBranch() == null) {
            return false;
        }
        return project.getDefaultBranch().equals(getBranchName());
    }

    /**
     * Helper method to get latest commit
     */
    public Commit getLatestCommit() {
        if (commits == null || commits.isEmpty()) {
            return null;
        }
        return commits.get(commits.size() - 1);
    }
}
