package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.DiffCalculator;
import com.gitlab.mirror.server.service.monitor.LocalCacheManager;
import com.gitlab.mirror.server.service.monitor.UnifiedProjectMonitor;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ScanResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sync Controller
 * <p>
 * REST API for project synchronization and monitoring.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final UnifiedProjectMonitor unifiedProjectMonitor;
    private final SyncProjectMapper syncProjectMapper;
    private final DiffCalculator diffCalculator;
    private final LocalCacheManager cacheManager;

    public SyncController(
            UnifiedProjectMonitor unifiedProjectMonitor,
            SyncProjectMapper syncProjectMapper,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager) {
        this.unifiedProjectMonitor = unifiedProjectMonitor;
        this.syncProjectMapper = syncProjectMapper;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
    }

    /**
     * Trigger manual scan
     *
     * POST /api/sync/scan?type=incremental
     */
    @PostMapping("/scan")
    public ResponseEntity<ApiResponse<ScanResult>> triggerScan(
            @RequestParam(defaultValue = "incremental") String type) {
        log.info("Manual scan triggered - type: {}", type);

        try {
            ScanResult result = unifiedProjectMonitor.scan(type);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Scan failed", e);
            return ResponseEntity.ok(ApiResponse.error("Scan failed: " + e.getMessage()));
        }
    }

    /**
     * Get project list with pagination
     *
     * GET /api/sync/projects?status=synced&page=1&size=20
     */
    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<PageResult<SyncProject>>> getProjects(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query projects - status: {}, page: {}, size: {}", status, page, size);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("sync_status", status);
            }

            Page<SyncProject> pageQuery = new Page<>(page, size);
            Page<SyncProject> result = syncProjectMapper.selectPage(pageQuery, queryWrapper);

            PageResult<SyncProject> pageResult = new PageResult<>();
            pageResult.setItems(result.getRecords());
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query projects failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project details
     *
     * GET /api/sync/projects/{projectKey}
     */
    @GetMapping("/projects/{projectKey}")
    public ResponseEntity<ApiResponse<SyncProject>> getProjectDetails(
            @PathVariable String projectKey) {
        log.info("Query project details - projectKey: {}", projectKey);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("project_key", projectKey);
            SyncProject project = syncProjectMapper.selectOne(queryWrapper);

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            return ResponseEntity.ok(ApiResponse.success(project));
        } catch (Exception e) {
            log.error("Query project details failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project diff list
     *
     * GET /api/sync/diffs?status=OUTDATED&page=1&size=20
     */
    @GetMapping("/diffs")
    public ResponseEntity<ApiResponse<PageResult<ProjectDiff>>> getProjectDiffs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query project diffs - status: {}, page: {}, size: {}", status, page, size);

        try {
            QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("sync_status", status);
            }

            Page<SyncProject> pageQuery = new Page<>(page, size);
            Page<SyncProject> result = syncProjectMapper.selectPage(pageQuery, queryWrapper);

            // Calculate diffs for each project
            List<ProjectDiff> diffs = result.getRecords().stream()
                    .map(project -> {
                        // Try cache first
                        ProjectDiff cachedDiff = cacheManager.get("diff:" + project.getProjectKey());
                        if (cachedDiff != null) {
                            return cachedDiff;
                        }

                        // Calculate and cache
                        ProjectDiff diff = diffCalculator.calculateDiff(project.getId());
                        if (diff != null) {
                            cacheManager.put("diff:" + project.getProjectKey(), diff, 15);
                        }
                        return diff;
                    })
                    .filter(diff -> diff != null)
                    .toList();

            PageResult<ProjectDiff> pageResult = new PageResult<>();
            pageResult.setItems(diffs);
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query project diffs failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project diff
     *
     * GET /api/sync/diff?projectKey=devops/gitlab-mirror
     * GET /api/sync/diff?syncProjectId=984
     */
    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<ProjectDiff>> getProjectDiff(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) Long syncProjectId) {
        log.info("Query project diff - projectKey: {}, syncProjectId: {}", projectKey, syncProjectId);

        try {
            SyncProject project = null;

            // Query by syncProjectId first (if provided)
            if (syncProjectId != null) {
                project = syncProjectMapper.selectById(syncProjectId);
                if (project != null) {
                    projectKey = project.getProjectKey();
                }
            }
            // Query by projectKey (if provided and not found by ID)
            else if (projectKey != null && !projectKey.isEmpty()) {
                // Try to get from cache first
                ProjectDiff cachedDiff = cacheManager.get("diff:" + projectKey);
                if (cachedDiff != null) {
                    log.debug("Diff found in cache for project: {}", projectKey);
                    return ResponseEntity.ok(ApiResponse.success(cachedDiff));
                }

                QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("project_key", projectKey);
                project = syncProjectMapper.selectOne(queryWrapper);
            } else {
                return ResponseEntity.ok(ApiResponse.error("Either projectKey or syncProjectId must be provided"));
            }

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            ProjectDiff diff = diffCalculator.calculateDiff(project.getId());
            if (diff != null && projectKey != null) {
                // Cache the result
                cacheManager.put("diff:" + projectKey, diff, 15);
            }

            return ResponseEntity.ok(ApiResponse.success(diff));
        } catch (Exception e) {
            log.error("Query project diff failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * API Response wrapper
     */
    @Data
    public static class ApiResponse<T> {
        private boolean success;
        private T data;
        private String message;

        public static <T> ApiResponse<T> success(T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(true);
            response.setData(data);
            return response;
        }

        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.setSuccess(false);
            response.setMessage(message);
            return response;
        }
    }

    /**
     * Page result wrapper
     */
    @Data
    public static class PageResult<T> {
        private List<T> items;
        private Long total;
        private Integer page;
        private Integer size;
    }
}
