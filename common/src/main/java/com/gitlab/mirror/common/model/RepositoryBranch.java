package com.gitlab.mirror.common.model;

import lombok.Data;

/**
 * GitLab Repository Branch Model
 *
 * @author GitLab Mirror Team
 */
@Data
public class RepositoryBranch {

    private String name;

    private Commit commit;

    private Boolean merged;

    @Data
    public static class Commit {
        private String id;
        private String message;
        private String authorName;
        private String authorEmail;
        private String committedDate;
    }
}
