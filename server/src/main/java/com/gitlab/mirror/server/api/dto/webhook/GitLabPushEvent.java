package com.gitlab.mirror.server.api.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * GitLab Push Event Webhook Payload
 *
 * @author GitLab Mirror Team
 */
@Data
public class GitLabPushEvent {

    @JsonProperty("object_kind")
    private String objectKind;

    @JsonProperty("event_name")
    private String eventName;

    private String before;
    private String after;
    private String ref;

    @JsonProperty("checkout_sha")
    private String checkoutSha;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("user_username")
    private String userUsername;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("project_id")
    private Long projectId;

    private ProjectInfo project;
    private List<Commit> commits;

    @JsonProperty("total_commits_count")
    private Integer totalCommitsCount;

    @Data
    public static class ProjectInfo {
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
        private String visibility;

        @JsonProperty("path_with_namespace")
        private String pathWithNamespace;

        @JsonProperty("default_branch")
        private String defaultBranch;

        private String homepage;
        private String url;

        @JsonProperty("ssh_url")
        private String sshUrl;

        @JsonProperty("http_url")
        private String httpUrl;
    }

    @Data
    public static class Commit {
        private String id;
        private String message;
        private String timestamp;
        private String url;
        private Author author;
        private List<String> added;
        private List<String> modified;
        private List<String> removed;
    }

    @Data
    public static class Author {
        private String name;
        private String email;
    }
}
