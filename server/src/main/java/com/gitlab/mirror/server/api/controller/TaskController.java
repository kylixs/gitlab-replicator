package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.*;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.SyncTask;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task Management API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final SyncTaskMapper syncTaskMapper;
    private final SyncProjectMapper syncProjectMapper;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SyncEventMapper syncEventMapper;

    /**
     * List tasks with filters and pagination
     *
     * GET /api/tasks?type=pull&status=waiting&priority=high&page=1&size=20
     */
    @GetMapping
    public ApiResponse<PageResponse<TaskDTO>> listTasks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Listing tasks: type={}, status={}, priority={}, enabled={}, page={}, size={}",
                type, status, priority, enabled, page, size);

        LambdaQueryWrapper<SyncTask> wrapper = new LambdaQueryWrapper<>();

        if (type != null) {
            wrapper.eq(SyncTask::getTaskType, type);
        }
        if (status != null) {
            wrapper.eq(SyncTask::getTaskStatus, status);
        }

        wrapper.orderByAsc(SyncTask::getNextRunAt);

        Page<SyncTask> pageRequest = new Page<>(page, size);
        IPage<SyncTask> result = syncTaskMapper.selectPage(pageRequest, wrapper);

        // Convert to DTOs with filtering
        List<TaskDTO> dtoList = result.getRecords().stream()
                .map(this::toDTO)
                .filter(dto -> {
                    // Apply priority filter
                    if (priority != null && !priority.equals(dto.getPriority())) {
                        return false;
                    }
                    // Apply enabled filter
                    if (enabled != null && !enabled.equals(dto.getEnabled())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        PageResponse<TaskDTO> pageResponse = PageResponse.of(
                dtoList,
                result.getTotal(),
                (int) result.getCurrent(),
                (int) result.getSize()
        );

        return ApiResponse.success(pageResponse);
    }

    /**
     * Get task details by task ID
     *
     * GET /api/tasks/{taskId}
     */
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDTO> getTask(@PathVariable Long taskId) {
        log.info("Getting task details for taskId={}", taskId);

        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        return ApiResponse.success(toDTO(task));
    }

    /**
     * Manually retry a task
     *
     * POST /api/tasks/{taskId}/retry
     */
    @PostMapping("/{taskId}/retry")
    public ApiResponse<TaskDTO> retryTask(@PathVariable Long taskId) {
        log.info("Manually retrying task: taskId={}", taskId);

        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        // Update task for immediate retry
        task.setNextRunAt(Instant.now());
        task.setTaskStatus("waiting");
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);

        log.info("Task scheduled for immediate retry: taskId={}", taskId);

        return ApiResponse.success(toDTO(task));
    }

    /**
     * Reset task failure count
     *
     * PUT /api/tasks/{taskId}/reset-failures
     */
    @PutMapping("/{taskId}/reset-failures")
    public ApiResponse<TaskDTO> resetFailures(
            @PathVariable Long taskId,
            @RequestBody(required = false) ResetFailuresRequest request) {

        log.info("Resetting failure count for task: taskId={}", taskId);

        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        // Reset failures and re-enable if disabled
        task.setConsecutiveFailures(0);
        task.setTaskStatus("waiting");
        task.setErrorMessage(null);
        task.setUpdatedAt(LocalDateTime.now());
        syncTaskMapper.updateById(task);

        // Re-enable project if disabled
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project != null && !project.getEnabled()) {
            project.setEnabled(true);
            project.setUpdatedAt(LocalDateTime.now());
            syncProjectMapper.updateById(project);
        }

        // Re-enable Pull config if disabled
        PullSyncConfig config = pullSyncConfigMapper.selectOne(
                new LambdaQueryWrapper<PullSyncConfig>()
                        .eq(PullSyncConfig::getSyncProjectId, task.getSyncProjectId())
        );
        if (config != null && !config.getEnabled()) {
            config.setEnabled(true);
            config.setUpdatedAt(LocalDateTime.now());
            pullSyncConfigMapper.updateById(config);
        }

        log.info("Failure count reset and project re-enabled: taskId={}", taskId);

        return ApiResponse.success(toDTO(task));
    }

    /**
     * Get task execution history
     *
     * GET /api/tasks/{taskId}/history?page=1&size=20
     */
    @GetMapping("/{taskId}/history")
    public ApiResponse<PageResponse<EventDTO>> getTaskHistory(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Getting execution history for taskId={}, page={}, size={}", taskId, page, size);

        // Verify task exists
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }

        // Query events for this task's project
        Page<SyncEvent> pageRequest = new Page<>(page, size);
        IPage<SyncEvent> result = syncEventMapper.selectPageWithFilters(
                pageRequest,
                task.getSyncProjectId(),
                null,  // eventType (all types)
                null,  // status (all statuses)
                null,  // startTime
                null   // endTime
        );

        // Get project key
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        String projectKey = project != null ? project.getProjectKey() : null;

        // Convert to DTOs
        List<EventDTO> eventDTOs = result.getRecords().stream()
                .map(event -> EventDTO.from(event, projectKey))
                .collect(Collectors.toList());

        PageResponse<EventDTO> pageResponse = new PageResponse<>();
        pageResponse.setItems(eventDTOs);
        pageResponse.setTotal(result.getTotal());
        pageResponse.setPage((int) result.getCurrent());
        pageResponse.setPageSize((int) result.getSize());
        pageResponse.setTotalPages((int) result.getPages());

        return ApiResponse.success(pageResponse);
    }

    /**
     * Get task statistics
     *
     * GET /api/tasks/stats
     */
    @GetMapping("/stats")
    public ApiResponse<TaskStatsDTO> getTaskStats() {
        log.info("Getting task statistics");

        TaskStatsDTO stats = new TaskStatsDTO();

        // Total tasks
        stats.setTotalTasks(syncTaskMapper.selectCount(null));

        // By type
        stats.setPullTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskType, "pull")
        ));
        stats.setPushTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskType, "push")
        ));

        // By status
        stats.setWaitingTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskStatus, "waiting")
        ));
        stats.setRunningTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskStatus, "running")
        ));
        stats.setCompletedTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .eq(SyncTask::getTaskStatus, "waiting")
                        .eq(SyncTask::getLastSyncStatus, "success")
        ));
        stats.setFailedTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .ge(SyncTask::getConsecutiveFailures, 1)
        ));
        stats.setDisabledTasks(syncTaskMapper.selectCount(
                new LambdaQueryWrapper<SyncTask>()
                        .ge(SyncTask::getConsecutiveFailures, 5)
        ));

        // Pull sync priority stats
        List<SyncTask> pullTasks = syncTaskMapper.selectList(
                new LambdaQueryWrapper<SyncTask>().eq(SyncTask::getTaskType, "pull")
        );

        long critical = 0, high = 0, normal = 0, low = 0;
        for (SyncTask task : pullTasks) {
            PullSyncConfig config = pullSyncConfigMapper.selectOne(
                    new LambdaQueryWrapper<PullSyncConfig>()
                            .eq(PullSyncConfig::getSyncProjectId, task.getSyncProjectId())
            );
            if (config != null) {
                switch (config.getPriority()) {
                    case "critical": critical++; break;
                    case "high": high++; break;
                    case "normal": normal++; break;
                    case "low": low++; break;
                }
            }
        }

        stats.setCriticalTasks(critical);
        stats.setHighPriorityTasks(high);
        stats.setNormalPriorityTasks(normal);
        stats.setLowPriorityTasks(low);

        return ApiResponse.success(stats);
    }

    /**
     * Convert entity to DTO
     */
    private TaskDTO toDTO(SyncTask task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setSyncProjectId(task.getSyncProjectId());
        dto.setTaskType(task.getTaskType());
        dto.setTaskStatus(task.getTaskStatus());
        dto.setNextRunAt(task.getNextRunAt());
        dto.setLastRunAt(task.getLastRunAt());
        dto.setLastSyncStatus(task.getLastSyncStatus());
        dto.setConsecutiveFailures(task.getConsecutiveFailures());
        dto.setErrorMessage(task.getErrorMessage());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());

        // Get project info
        SyncProject project = syncProjectMapper.selectById(task.getSyncProjectId());
        if (project != null) {
            dto.setProjectKey(project.getProjectKey());
            dto.setSyncMethod(project.getSyncMethod());
            dto.setEnabled(project.getEnabled());
        }

        // Get Pull sync priority
        if ("pull".equals(task.getTaskType())) {
            PullSyncConfig config = pullSyncConfigMapper.selectOne(
                    new LambdaQueryWrapper<PullSyncConfig>()
                            .eq(PullSyncConfig::getSyncProjectId, task.getSyncProjectId())
            );
            if (config != null) {
                dto.setPriority(config.getPriority());
            }
        }

        return dto;
    }
}
