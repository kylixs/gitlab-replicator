package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Project List Service
 * <p>
 * Service for building project list with diff and delay information
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class ProjectListService {

    private final BranchSnapshotService branchSnapshotService;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;

    public ProjectListService(
            BranchSnapshotService branchSnapshotService,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper) {
        this.branchSnapshotService = branchSnapshotService;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
    }

    /**
     * Build project list DTO from sync project
     */
    public ProjectListDTO buildProjectListDTO(SyncProject project) {
        ProjectListDTO dto = new ProjectListDTO();
        dto.setId(project.getId());
        dto.setProjectKey(project.getProjectKey());
        dto.setSyncStatus(project.getSyncStatus());
        dto.setSyncMethod(project.getSyncMethod());
        dto.setLastSyncAt(project.getLastSyncAt());
        dto.setUpdatedAt(project.getUpdatedAt());

        // Get group path from source project info
        QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
        sourceQuery.eq("sync_project_id", project.getId());
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);
        if (sourceInfo != null) {
            dto.setGroupPath(sourceInfo.getGroupPath());
        }

        // Calculate diff
        ProjectListDTO.DiffInfo diff = calculateDiff(project.getId());
        dto.setDiff(diff);

        // Calculate delay
        Long delaySeconds = calculateDelay(project.getId());
        dto.setDelaySeconds(delaySeconds);
        dto.setDelayFormatted(formatDelay(delaySeconds));

        return dto;
    }

    /**
     * Calculate diff for project
     */
    private ProjectListDTO.DiffInfo calculateDiff(Long syncProjectId) {
        ProjectListDTO.DiffInfo diff = new ProjectListDTO.DiffInfo();

        try {
            List<ProjectBranchSnapshot> sourceBranches = branchSnapshotService.getBranchSnapshots(syncProjectId, "source");
            List<ProjectBranchSnapshot> targetBranches = branchSnapshotService.getBranchSnapshots(syncProjectId, "target");

            Map<String, ProjectBranchSnapshot> sourceMap = sourceBranches.stream()
                    .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));
            Map<String, ProjectBranchSnapshot> targetMap = targetBranches.stream()
                    .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));

            int branchNew = 0;
            int branchDeleted = 0;
            int branchOutdated = 0;
            int commitDiff = 0;

            // Count new and outdated branches
            for (ProjectBranchSnapshot source : sourceBranches) {
                ProjectBranchSnapshot target = targetMap.get(source.getBranchName());
                if (target == null) {
                    branchNew++;
                } else if (!source.getCommitSha().equals(target.getCommitSha())) {
                    branchOutdated++;
                    // Simplified commit diff (could be enhanced with actual commit count)
                    commitDiff += 1;
                }
            }

            // Count deleted branches
            for (ProjectBranchSnapshot target : targetBranches) {
                if (!sourceMap.containsKey(target.getBranchName())) {
                    branchDeleted++;
                }
            }

            diff.setBranchNew(branchNew);
            diff.setBranchDeleted(branchDeleted);
            diff.setBranchOutdated(branchOutdated);
            diff.setCommitDiff(commitDiff);

        } catch (Exception e) {
            log.warn("Failed to calculate diff for project {}: {}", syncProjectId, e.getMessage());
        }

        return diff;
    }

    /**
     * Calculate delay for project (in seconds)
     */
    private Long calculateDelay(Long syncProjectId) {
        try {
            QueryWrapper<SourceProjectInfo> sourceQuery = new QueryWrapper<>();
            sourceQuery.eq("sync_project_id", syncProjectId);
            SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceQuery);

            QueryWrapper<TargetProjectInfo> targetQuery = new QueryWrapper<>();
            targetQuery.eq("sync_project_id", syncProjectId);
            TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetQuery);

            if (sourceInfo != null && targetInfo != null &&
                    sourceInfo.getLastActivityAt() != null && targetInfo.getLastActivityAt() != null) {
                Duration duration = Duration.between(targetInfo.getLastActivityAt(), sourceInfo.getLastActivityAt());
                return duration.getSeconds();
            }
        } catch (Exception e) {
            log.warn("Failed to calculate delay for project {}: {}", syncProjectId, e.getMessage());
        }

        return 0L;
    }

    /**
     * Format delay seconds to human-readable string
     */
    private String formatDelay(Long seconds) {
        if (seconds == null || seconds < 0) {
            return "未知";
        }

        if (seconds < 60) {
            return "刚刚";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "分钟";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "小时";
        } else {
            long days = seconds / 86400;
            return days + "天";
        }
    }
}
