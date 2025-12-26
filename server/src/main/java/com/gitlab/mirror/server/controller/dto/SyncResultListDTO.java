package com.gitlab.mirror.server.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Sync Result List DTO
 *
 * @author GitLab Mirror Team
 */
@Data
public class SyncResultListDTO {

    /**
     * Sync Result ID
     */
    private Long id;

    /**
     * Sync Project ID
     */
    private Long syncProjectId;

    /**
     * Project Key
     */
    private String projectKey;

    /**
     * Last sync time
     */
    private LocalDateTime lastSyncAt;

    /**
     * Sync start time
     */
    private LocalDateTime startedAt;

    /**
     * Sync completion time
     */
    private LocalDateTime completedAt;

    /**
     * Sync status: success/failed/skipped
     */
    private String syncStatus;

    /**
     * Has changes
     */
    private Boolean hasChanges;

    /**
     * Changes count
     */
    private Integer changesCount;

    /**
     * Source commit SHA
     */
    private String sourceCommitSha;

    /**
     * Target commit SHA
     */
    private String targetCommitSha;

    /**
     * Duration in seconds
     */
    private Integer durationSeconds;

    /**
     * Error message
     */
    private String errorMessage;

    /**
     * Summary
     */
    private String summary;

    /**
     * Sync method
     */
    private String syncMethod;
}
