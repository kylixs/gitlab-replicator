package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.api.dto.PageResponse;
import com.gitlab.mirror.server.api.dto.ProjectDTO;
import com.gitlab.mirror.server.api.exception.ResourceNotFoundException;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.ProjectDiscoveryService;
import com.gitlab.mirror.server.service.TargetProjectManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Project Management API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final SyncProjectMapper syncProjectMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final ProjectDiscoveryService projectDiscoveryService;
    private final TargetProjectManagementService targetProjectManagementService;

    public ProjectController(
            SyncProjectMapper syncProjectMapper,
            SourceProjectInfoMapper sourceProjectInfoMapper,
            TargetProjectInfoMapper targetProjectInfoMapper,
            ProjectDiscoveryService projectDiscoveryService,
            TargetProjectManagementService targetProjectManagementService) {
        this.syncProjectMapper = syncProjectMapper;
        this.sourceProjectInfoMapper = sourceProjectInfoMapper;
        this.targetProjectInfoMapper = targetProjectInfoMapper;
        this.projectDiscoveryService = projectDiscoveryService;
        this.targetProjectManagementService = targetProjectManagementService;
    }

    /**
     * Get project list with pagination and filters
     */
    @GetMapping
    public ApiResponse<PageResponse<ProjectDTO>> getProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String syncMethod,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();

        if (status != null) {
            wrapper.eq("sync_status", status);
        }
        if (syncMethod != null) {
            wrapper.eq("sync_method", syncMethod);
        }

        wrapper.orderByDesc("updated_at");

        Page<SyncProject> pageRequest = new Page<>(page, size);
        IPage<SyncProject> result = syncProjectMapper.selectPage(pageRequest, wrapper);

        // Convert to DTO
        List<ProjectDTO> dtoList = result.getRecords().stream()
                .map(this::toProjectDTO)
                .collect(Collectors.toList());

        PageResponse<ProjectDTO> pageResponse = PageResponse.of(
                dtoList,
                result.getTotal(),
                (int) result.getCurrent(),
                (int) result.getSize()
        );

        return ApiResponse.success(pageResponse);
    }

    private ProjectDTO toProjectDTO(SyncProject project) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(project.getId());
        dto.setProjectKey(project.getProjectKey());
        dto.setSyncMethod(project.getSyncMethod());
        dto.setSyncStatus(project.getSyncStatus());
        dto.setEnabled(project.getEnabled());
        dto.setErrorMessage(project.getErrorMessage());
        dto.setCreatedAt(project.getCreatedAt());
        dto.setUpdatedAt(project.getUpdatedAt());
        return dto;
    }

    /**
     * Get project details by key
     */
    @GetMapping("/{key:.+}")
    public ApiResponse<Map<String, Object>> getProject(@PathVariable String key) {
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", key);
        SyncProject project = syncProjectMapper.selectOne(wrapper);

        if (project == null) {
            throw new ResourceNotFoundException("Project", key);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("project", project);

        // Get source project info
        QueryWrapper<SourceProjectInfo> sourceWrapper = new QueryWrapper<>();
        sourceWrapper.eq("sync_project_id", project.getId());
        SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(sourceWrapper);
        details.put("source", sourceInfo);

        // Get target project info
        QueryWrapper<TargetProjectInfo> targetWrapper = new QueryWrapper<>();
        targetWrapper.eq("sync_project_id", project.getId());
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetWrapper);
        details.put("target", targetInfo);

        return ApiResponse.success(details);
    }

    /**
     * Trigger manual project discovery
     */
    @PostMapping("/discover")
    public ApiResponse<Map<String, Object>> discoverProjects(
            @RequestParam(required = false) String groupPath) {

        log.info("Manual project discovery triggered, groupPath={}", groupPath);
        int count = projectDiscoveryService.discoverProjects(groupPath);

        Map<String, Object> result = new HashMap<>();
        result.put("discovered_count", count);

        return ApiResponse.success(result, "Project discovery completed");
    }

    /**
     * Setup target project
     */
    @PostMapping("/{key:.+}/setup-target")
    public ApiResponse<TargetProjectInfo> setupTargetProject(@PathVariable String key) {
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", key);
        SyncProject project = syncProjectMapper.selectOne(wrapper);

        if (project == null) {
            throw new ResourceNotFoundException("Project", key);
        }

        log.info("Setting up target project for: {}", key);
        TargetProjectInfo targetInfo = targetProjectManagementService.createTargetProject(project.getId());

        return ApiResponse.success(targetInfo, "Target project created");
    }

    /**
     * Get target project info
     */
    @GetMapping("/{key:.+}/target")
    public ApiResponse<TargetProjectInfo> getTargetProject(@PathVariable String key) {
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("project_key", key);
        SyncProject project = syncProjectMapper.selectOne(wrapper);

        if (project == null) {
            throw new ResourceNotFoundException("Project", key);
        }

        QueryWrapper<TargetProjectInfo> targetWrapper = new QueryWrapper<>();
        targetWrapper.eq("sync_project_id", project.getId());
        TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetWrapper);

        if (targetInfo == null) {
            throw new ResourceNotFoundException("Target project for", key);
        }

        return ApiResponse.success(targetInfo);
    }
}
