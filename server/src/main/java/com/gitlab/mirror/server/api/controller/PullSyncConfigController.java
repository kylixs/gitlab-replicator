package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.*;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pull Sync Configuration API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/pull-sync/config")
@RequiredArgsConstructor
public class PullSyncConfigController {

    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SyncProjectMapper syncProjectMapper;
    private final SyncTaskMapper syncTaskMapper;

    /**
     * Get Pull sync configuration by project ID
     *
     * GET /api/pull-sync/config/{projectId}
     */
    @GetMapping("/{projectId}")
    public ApiResponse<PullSyncConfigDTO> getConfig(@PathVariable Long projectId) {
        log.info("Getting Pull sync config for projectId={}", projectId);

        // Get config
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
                new LambdaQueryWrapper<PullSyncConfig>()
                        .eq(PullSyncConfig::getSyncProjectId, projectId)
        );

        if (config == null) {
            throw new ResourceNotFoundException("Pull sync config not found for projectId: " + projectId);
        }

        // Get project for additional info
        SyncProject project = syncProjectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }

        // Get task for sync status
        SyncTask task = syncTaskMapper.selectOne(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getSyncProjectId, projectId)
        );

        PullSyncConfigDTO dto = toDTO(config, project, task);
        return ApiResponse.success(dto);
    }

    /**
     * List all Pull sync configurations with filters
     *
     * GET /api/pull-sync/config?priority=high&enabled=true&page=1&size=20
     */
    @GetMapping
    public ApiResponse<PageResponse<PullSyncConfigDTO>> listConfigs(
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Listing Pull sync configs: priority={}, enabled={}, page={}, size={}",
                priority, enabled, page, size);

        LambdaQueryWrapper<PullSyncConfig> wrapper = new LambdaQueryWrapper<>();

        if (priority != null) {
            wrapper.eq(PullSyncConfig::getPriority, priority);
        }
        if (enabled != null) {
            wrapper.eq(PullSyncConfig::getEnabled, enabled);
        }

        wrapper.orderByDesc(PullSyncConfig::getUpdatedAt);

        Page<PullSyncConfig> pageRequest = new Page<>(page, size);
        IPage<PullSyncConfig> result = pullSyncConfigMapper.selectPage(pageRequest, wrapper);

        // Convert to DTOs
        List<PullSyncConfigDTO> dtoList = result.getRecords().stream()
                .map(config -> {
                    SyncProject project = syncProjectMapper.selectById(config.getSyncProjectId());
                    SyncTask task = syncTaskMapper.selectOne(
                            new LambdaQueryWrapper<SyncTask>()
                                    .eq(SyncTask::getSyncProjectId, config.getSyncProjectId())
                    );
                    return toDTO(config, project, task);
                })
                .collect(Collectors.toList());

        PageResponse<PullSyncConfigDTO> pageResponse = PageResponse.of(
                dtoList,
                result.getTotal(),
                (int) result.getCurrent(),
                (int) result.getSize()
        );

        return ApiResponse.success(pageResponse);
    }

    /**
     * Update Pull sync priority
     *
     * PUT /api/pull-sync/config/{projectId}/priority
     * Body: {"priority": "high"}
     */
    @PutMapping("/{projectId}/priority")
    public ApiResponse<PullSyncConfigDTO> updatePriority(
            @PathVariable Long projectId,
            @RequestBody UpdatePriorityRequest request) {

        log.info("Updating priority for projectId={} to {}", projectId, request.getPriority());

        // Get config
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
                new LambdaQueryWrapper<PullSyncConfig>()
                        .eq(PullSyncConfig::getSyncProjectId, projectId)
        );

        if (config == null) {
            throw new ResourceNotFoundException("Pull sync config not found for projectId: " + projectId);
        }

        // Update priority
        config.setPriority(request.getPriority());
        config.setUpdatedAt(LocalDateTime.now());
        pullSyncConfigMapper.updateById(config);

        log.info("Priority updated successfully: projectId={}, priority={}",
                projectId, request.getPriority());

        // Return updated config
        SyncProject project = syncProjectMapper.selectById(projectId);
        SyncTask task = syncTaskMapper.selectOne(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getSyncProjectId, projectId)
        );

        return ApiResponse.success(toDTO(config, project, task));
    }

    /**
     * Update Pull sync enabled status
     *
     * PUT /api/pull-sync/config/{projectId}/enabled
     * Body: {"enabled": true}
     */
    @PutMapping("/{projectId}/enabled")
    public ApiResponse<PullSyncConfigDTO> updateEnabled(
            @PathVariable Long projectId,
            @RequestBody UpdateEnabledRequest request) {

        log.info("Updating enabled status for projectId={} to {}", projectId, request.getEnabled());

        // Get config
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
                new LambdaQueryWrapper<PullSyncConfig>()
                        .eq(PullSyncConfig::getSyncProjectId, projectId)
        );

        if (config == null) {
            throw new ResourceNotFoundException("Pull sync config not found for projectId: " + projectId);
        }

        // Get project
        SyncProject project = syncProjectMapper.selectById(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }

        // Update enabled status in both config and project
        config.setEnabled(request.getEnabled());
        config.setUpdatedAt(LocalDateTime.now());
        pullSyncConfigMapper.updateById(config);

        project.setEnabled(request.getEnabled());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.updateById(project);

        log.info("Enabled status updated successfully: projectId={}, enabled={}",
                projectId, request.getEnabled());

        // Return updated config
        SyncTask task = syncTaskMapper.selectOne(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getSyncProjectId, projectId)
        );

        return ApiResponse.success(toDTO(config, project, task));
    }

    /**
     * Convert entity to DTO
     */
    private PullSyncConfigDTO toDTO(PullSyncConfig config, SyncProject project, SyncTask task) {
        PullSyncConfigDTO dto = new PullSyncConfigDTO();
        dto.setId(config.getId());
        dto.setSyncProjectId(config.getSyncProjectId());
        dto.setPriority(config.getPriority());
        dto.setEnabled(config.getEnabled());
        dto.setLocalRepoPath(config.getLocalRepoPath());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setUpdatedAt(config.getUpdatedAt());

        if (project != null) {
            dto.setProjectKey(project.getProjectKey());
            dto.setSyncStatus(project.getSyncStatus());
        }

        if (task != null) {
            dto.setLastSyncAt(task.getLastRunAt() != null
                    ? LocalDateTime.ofInstant(task.getLastRunAt(), java.time.ZoneId.systemDefault())
                    : null);
            dto.setConsecutiveFailures(task.getConsecutiveFailures());
        }

        return dto;
    }
}
