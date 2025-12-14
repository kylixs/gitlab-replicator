package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Project Snapshot
 * <p>
 * Represents a snapshot of a GitLab project's state at a specific point in time.
 * Used for comparing source and target projects.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSnapshot {

    /**
     * Latest commit SHA (default branch)
     */
    private String commitSha;

    /**
     * Total number of commits (optional, can be expensive to query)
     */
    private Integer commitCount;

    /**
     * Number of branches
     */
    private Integer branchCount;

    /**
     * Repository size in bytes
     */
    private Long sizeBytes;

    /**
     * Last activity timestamp
     */
    private LocalDateTime lastActivityAt;

    /**
     * Default branch name
     */
    private String defaultBranch;
}
