package com.gitlab.mirror.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * GitLab Repository Branch Model
 * <p>
 * Maps to GitLab API response: /api/v4/projects/:id/repository/branches
 *
 * @author GitLab Mirror Team
 */
@Data
public class RepositoryBranch {

    /**
     * Branch name
     */
    private String name;

    /**
     * Latest commit information
     */
    private Commit commit;

    /**
     * Whether the branch has been merged
     */
    private Boolean merged;

    /**
     * Whether the branch is protected
     */
    @JsonProperty("protected")
    private Boolean isProtected;

    /**
     * Whether this is the default branch
     * Note: 'default' is a Java keyword, so we use JsonProperty
     */
    @JsonProperty("default")
    private Boolean isDefault;

    /**
     * Web URL of the branch
     */
    private String webUrl;

    /**
     * Commit information
     */
    @Data
    public static class Commit {
        /**
         * Commit SHA (full)
         */
        private String id;

        /**
         * Commit message
         */
        private String message;

        /**
         * Author name
         */
        @JsonProperty("author_name")
        private String authorName;

        /**
         * Author email
         */
        @JsonProperty("author_email")
        private String authorEmail;

        /**
         * Commit timestamp
         */
        @JsonProperty("committed_date")
        private OffsetDateTime committedDate;

        /**
         * Authored timestamp
         */
        @JsonProperty("authored_date")
        private OffsetDateTime authoredDate;

        /**
         * Committer name
         */
        @JsonProperty("committer_name")
        private String committerName;

        /**
         * Committer email
         */
        @JsonProperty("committer_email")
        private String committerEmail;
    }

}
