package com.gitlab.mirror.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GitLab Project Model
 *
 * @author GitLab Mirror Team
 */
@Data
public class GitLabProject {

    private Long id;

    private String name;

    private String path;

    @JsonProperty("path_with_namespace")
    private String pathWithNamespace;

    private String description;

    @JsonProperty("default_branch")
    private String defaultBranch;

    private String visibility;

    @JsonProperty("web_url")
    private String webUrl;

    @JsonProperty("ssh_url_to_repo")
    private String sshUrlToRepo;

    @JsonProperty("http_url_to_repo")
    private String httpUrlToRepo;

    private Boolean archived;

    @JsonProperty("empty_repo")
    private Boolean emptyRepo;

    @JsonProperty("star_count")
    private Integer starCount;

    @JsonProperty("forks_count")
    private Integer forksCount;

    @JsonProperty("last_activity_at")
    private LocalDateTime lastActivityAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private Namespace namespace;

    @Data
    public static class Namespace {
        private Long id;
        private String name;
        private String path;

        @JsonProperty("full_path")
        private String fullPath;
    }
}
