package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discovery Result Statistics
 * <p>
 * Contains statistics about project discovery operation.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryResult {

    /**
     * Number of new projects detected
     */
    private Integer newProjects;

    /**
     * Number of updated projects detected
     */
    private Integer updatedProjects;

    /**
     * Number of deleted projects detected
     */
    private Integer deletedProjects;

    /**
     * Number of projects requiring sync
     */
    private Integer projectsNeedingSync;
}
