package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.api.dto.PageResponse;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
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

    public MirrorController(
            SyncProjectMapper syncProjectMapper,
            PushMirrorConfigMapper pushMirrorConfigMapper,
            PushMirrorManagementService pushMirrorManagementService) {
        this.syncProjectMapper = syncProjectMapper;
        this.pushMirrorConfigMapper = pushMirrorConfigMapper;
        this.pushMirrorManagementService = pushMirrorManagementService;
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
    public ApiResponse<PageResponse<PushMirrorConfig>> getMirrors(
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

        return ApiResponse.success(PageResponse.of(result));
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
}
