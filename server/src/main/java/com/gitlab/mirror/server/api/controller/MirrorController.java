package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.api.dto.MirrorDTO;
import com.gitlab.mirror.server.api.dto.PageResponse;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.scheduler.MirrorCompensationScheduler;
import com.gitlab.mirror.server.service.PushMirrorManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mirror Management API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/mirrors")
public class MirrorController {

    private final SyncProjectMapper syncProjectMapper;
    private final PushMirrorConfigMapper pushMirrorConfigMapper;
    private final PushMirrorManagementService pushMirrorManagementService;
    private final MirrorCompensationScheduler mirrorCompensationScheduler;

    public MirrorController(
            SyncProjectMapper syncProjectMapper,
            PushMirrorConfigMapper pushMirrorConfigMapper,
            PushMirrorManagementService pushMirrorManagementService,
            MirrorCompensationScheduler mirrorCompensationScheduler) {
        this.syncProjectMapper = syncProjectMapper;
        this.pushMirrorConfigMapper = pushMirrorConfigMapper;
        this.pushMirrorManagementService = pushMirrorManagementService;
        this.mirrorCompensationScheduler = mirrorCompensationScheduler;
    }

    /**
     * Setup mirror for a project
     */
    @PostMapping("/{key:.+}/setup")
    public ApiResponse<PushMirrorConfig> setupMirror(@PathVariable String key) {
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", key);
        SyncProject project = syncProjectMapper.selectOne(wrapper);

        if (project == null) {
            throw new ResourceNotFoundException("Project", key);
        }

        log.info("Setting up mirror for project: {}", key);
        PushMirrorConfig config = pushMirrorManagementService.configureMirror(project.getId());

        return ApiResponse.success(config, "Mirror configured");
    }

    /**
     * Get mirror list with pagination and filters
     */
    @GetMapping
    public ApiResponse<PageResponse<MirrorDTO>> getMirrors(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        QueryWrapper<PushMirrorConfig> wrapper = new QueryWrapper<>();

        if (status != null) {
            wrapper.eq("last_update_status", status);
        }

        wrapper.orderByDesc("updated_at");

        Page<PushMirrorConfig> pageRequest = new Page<>(page, size);
        IPage<PushMirrorConfig> result = pushMirrorConfigMapper.selectPage(pageRequest, wrapper);

        // Convert to DTO with project information
        List<MirrorDTO> dtoList = result.getRecords().stream()
                .map(this::toMirrorDTO)
                .collect(Collectors.toList());

        PageResponse<MirrorDTO> pageResponse = PageResponse.of(
                dtoList,
                result.getTotal(),
                (int) result.getCurrent(),
                (int) result.getSize()
        );

        return ApiResponse.success(pageResponse);
    }

    private MirrorDTO toMirrorDTO(PushMirrorConfig config) {
        MirrorDTO dto = new MirrorDTO();
        dto.setGitlabMirrorId(config.getGitlabMirrorId());
        dto.setSyncProjectId(config.getSyncProjectId());
        dto.setMirrorUrl(config.getMirrorUrl());
        dto.setLastUpdateStatus(config.getLastUpdateStatus());
        dto.setLastUpdateAt(config.getLastUpdateAt());
        dto.setLastSuccessfulUpdateAt(config.getLastSuccessfulUpdateAt());
        dto.setConsecutiveFailures(config.getConsecutiveFailures());
        dto.setErrorMessage(config.getErrorMessage());

        // Get project key
        SyncProject project = syncProjectMapper.selectById(config.getSyncProjectId());
        if (project != null) {
            dto.setProjectKey(project.getProjectKey());
        }

        return dto;
    }

    /**
     * Trigger manual mirror status polling
     */
    @PostMapping("/poll")
    public ApiResponse<Map<String, Object>> pollMirrorStatus() {
        log.info("Manual mirror status polling triggered");

        List<PushMirrorConfig> allConfigs = pushMirrorConfigMapper.selectList(null);
        List<Long> syncProjectIds = allConfigs.stream()
                .map(PushMirrorConfig::getSyncProjectId)
                .collect(Collectors.toList());

        int changedCount = pushMirrorManagementService.batchPollMirrorStatus(syncProjectIds);

        Map<String, Object> result = new HashMap<>();
        result.put("total_checked", syncProjectIds.size());
        result.put("changed_count", changedCount);

        return ApiResponse.success(result, "Mirror polling completed");
    }

    /**
     * Trigger manual mirror compensation check
     *
     * This endpoint manually triggers the compensation scheduler to check for
     * projects that need mirror configuration and creates mirrors for them.
     */
    @PostMapping("/compensate")
    public ApiResponse<String> compensateMirrors() {
        log.info("Manual mirror compensation triggered");

        mirrorCompensationScheduler.compensateMirrorConfiguration();

        return ApiResponse.success(null, "Mirror compensation check completed");
    }

    /**
     * Trigger manual push mirror sync for a specific project
     *
     * @param projectKey The project key (path with namespace)
     */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> triggerMirrorSync(@RequestParam("project") String projectKey) {
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", projectKey);
        SyncProject project = syncProjectMapper.selectOne(wrapper);

        if (project == null) {
            throw new ResourceNotFoundException("Project", projectKey);
        }

        log.info("Triggering mirror sync for project: {}", projectKey);

        // Trigger sync via service
        pushMirrorManagementService.triggerSync(project.getId());

        // Get mirror config for response
        PushMirrorConfig config = pushMirrorConfigMapper.selectBySyncProjectId(project.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("project_key", projectKey);
        result.put("mirror_id", config.getGitlabMirrorId());
        result.put("status", "triggered");

        return ApiResponse.success(result, "Mirror sync triggered");
    }
}
