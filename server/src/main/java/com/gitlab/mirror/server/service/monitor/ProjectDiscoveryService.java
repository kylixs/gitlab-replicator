package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.DiscoveryResult;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project Discovery Service
 * <p>
 * Detects new, updated, and deleted projects based on diff results.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class ProjectDiscoveryService {

    private final SyncProjectMapper syncProjectMapper;
    private final MonitorAlertMapper monitorAlertMapper;

    public ProjectDiscoveryService(
            SyncProjectMapper syncProjectMapper,
            MonitorAlertMapper monitorAlertMapper) {
        this.syncProjectMapper = syncProjectMapper;
        this.monitorAlertMapper = monitorAlertMapper;
    }

    /**
     * Detect new projects (target missing)
     *
     * @param diffs List of project diffs
     * @return Number of new projects detected
     */
    @Transactional(rollbackFor = Exception.class)
    public int detectNewProjects(List<ProjectDiff> diffs) {
        log.info("Detecting new projects from {} diffs", diffs.size());

        List<ProjectDiff> newProjects = diffs.stream()
                .filter(d -> d.getStatus() == ProjectDiff.SyncStatus.FAILED && d.getTarget() == null)
                .collect(Collectors.toList());

        log.info("Found {} new projects (target missing)", newProjects.size());

        for (ProjectDiff diff : newProjects) {
            // Check if project already exists
            SyncProject existing = syncProjectMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SyncProject>()
                            .eq("project_key", diff.getProjectKey())
            );

            if (existing == null) {
                // Create new sync project record
                SyncProject syncProject = new SyncProject();
                syncProject.setProjectKey(diff.getProjectKey());
                syncProject.setSyncMethod("pull_mirror");
                syncProject.setSyncStatus("pending");
                syncProject.setCreatedAt(LocalDateTime.now());
                syncProject.setUpdatedAt(LocalDateTime.now());

                syncProjectMapper.insert(syncProject);
                log.info("Created new sync project: {}", diff.getProjectKey());
            } else {
                log.debug("Sync project already exists: {}", diff.getProjectKey());
            }
        }

        return newProjects.size();
    }

    /**
     * Detect updated projects (changes detected)
     *
     * @param diffs List of project diffs
     * @return Number of updated projects detected
     */
    public int detectUpdatedProjects(List<ProjectDiff> diffs) {
        log.info("Detecting updated projects from {} diffs", diffs.size());

        List<ProjectDiff> updatedProjects = diffs.stream()
                .filter(d -> d.getStatus() == ProjectDiff.SyncStatus.OUTDATED)
                .filter(d -> !d.getDiff().isCommitShaMatches())
                .collect(Collectors.toList());

        log.info("Found {} updated projects (commit SHA changed)", updatedProjects.size());

        // Note: Actual sync triggering would be done by PullSyncExecutor
        // For now, we just log the detection
        for (ProjectDiff diff : updatedProjects) {
            log.debug("Project {} needs sync: {} commits behind",
                    diff.getProjectKey(), diff.getDiff().getCommitBehind());
        }

        return updatedProjects.size();
    }

    /**
     * Detect deleted projects
     *
     * @param existingProjectKeys Set of project keys from GitLab query
     * @return Number of deleted projects detected
     */
    @Transactional(rollbackFor = Exception.class)
    public int detectDeletedProjects(Set<String> existingProjectKeys) {
        log.info("Detecting deleted projects");

        // Get all sync projects from database
        List<SyncProject> allSyncProjects = syncProjectMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SyncProject>()
                        .ne("sync_status", "deleted")
        );

        int deletedCount = 0;
        for (SyncProject syncProject : allSyncProjects) {
            if (!existingProjectKeys.contains(syncProject.getProjectKey())) {
                // Project no longer exists in GitLab
                syncProject.setSyncStatus("deleted");
                syncProject.setUpdatedAt(LocalDateTime.now());
                syncProjectMapper.updateById(syncProject);

                // Create alert
                createDeletedProjectAlert(syncProject);

                deletedCount++;
                log.info("Marked project as deleted: {}", syncProject.getProjectKey());
            }
        }

        log.info("Detected {} deleted projects", deletedCount);
        return deletedCount;
    }

    /**
     * Perform full discovery on diff results
     *
     * @param diffs List of project diffs
     * @param existingProjectKeys Set of existing project keys from GitLab
     * @return Discovery result statistics
     */
    @Transactional(rollbackFor = Exception.class)
    public DiscoveryResult performDiscovery(List<ProjectDiff> diffs, Set<String> existingProjectKeys) {
        log.info("Performing project discovery on {} diffs", diffs.size());

        int newProjects = detectNewProjects(diffs);
        int updatedProjects = detectUpdatedProjects(diffs);
        int deletedProjects = detectDeletedProjects(existingProjectKeys);

        // Count projects needing sync (outdated or inconsistent)
        int projectsNeedingSync = (int) diffs.stream()
                .filter(d -> d.getStatus() == ProjectDiff.SyncStatus.OUTDATED ||
                             d.getStatus() == ProjectDiff.SyncStatus.INCONSISTENT)
                .count();

        return DiscoveryResult.builder()
                .newProjects(newProjects)
                .updatedProjects(updatedProjects)
                .deletedProjects(deletedProjects)
                .projectsNeedingSync(projectsNeedingSync)
                .build();
    }

    /**
     * Create alert for deleted project
     */
    private void createDeletedProjectAlert(SyncProject syncProject) {
        MonitorAlert alert = new MonitorAlert();
        alert.setSyncProjectId(syncProject.getId());
        alert.setAlertType("project_deleted");
        alert.setSeverity("medium");
        alert.setTitle("Project Deleted");
        alert.setDescription("Project " + syncProject.getProjectKey() + " no longer exists in source GitLab");
        alert.setStatus("active");
        alert.setTriggeredAt(LocalDateTime.now());
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());

        monitorAlertMapper.insert(alert);
        log.debug("Created alert for deleted project: {}", syncProject.getProjectKey());
    }
}
