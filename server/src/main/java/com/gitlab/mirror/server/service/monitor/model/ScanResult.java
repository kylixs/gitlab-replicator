package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scan Result Statistics
 * <p>
 * Contains statistics about a project scan operation.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {

    /**
     * Scan type (incremental/full)
     */
    private String scanType;

    /**
     * Scan duration in milliseconds
     */
    private Long durationMs;

    /**
     * Number of projects scanned
     */
    private Integer projectsScanned;

    /**
     * Number of projects updated
     */
    private Integer projectsUpdated;

    /**
     * Number of new projects discovered
     */
    private Integer newProjects;

    /**
     * Number of changes detected
     */
    private Integer changesDetected;

    /**
     * Detailed list of project changes
     */
    @Builder.Default
    private List<ProjectChange> projectChanges = new ArrayList<>();

    /**
     * Scan start time
     */
    private LocalDateTime startTime;

    /**
     * Scan end time
     */
    private LocalDateTime endTime;

    /**
     * Scan status (success/failed)
     */
    private String status;

    /**
     * Error message if failed
     */
    private String errorMessage;
}
