package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.BranchSnapshotService;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final BranchSnapshotService branchSnapshotService;

    public SyncController(
            UnifiedProjectMonitor unifiedProjectMonitor,
            SyncProjectMapper syncProjectMapper,
            DiffCalculator diffCalculator,
            LocalCacheManager cacheManager,
            BranchSnapshotService branchSnapshotService) {
        this.unifiedProjectMonitor = unifiedProjectMonitor;
        this.syncProjectMapper = syncProjectMapper;
        this.diffCalculator = diffCalculator;
        this.cacheManager = cacheManager;
        this.branchSnapshotService = branchSnapshotService;
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
                    .map(project -> diffCalculator.calculateDiff(project.getId()))
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

            return ResponseEntity.ok(ApiResponse.success(diff));
        } catch (Exception e) {
            log.error("Query project diff failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get project branches comparison
     *
     * GET /api/sync/branches?projectKey=ai/test-android-app-3
     * GET /api/sync/branches?syncProjectId=986
     */
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<BranchComparisonResult>> getProjectBranches(
            @RequestParam(required = false) String projectKey,
            @RequestParam(required = false) Long syncProjectId) {
        log.info("Query project branches - projectKey: {}, syncProjectId: {}", projectKey, syncProjectId);

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
                QueryWrapper<SyncProject> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("project_key", projectKey);
                project = syncProjectMapper.selectOne(queryWrapper);
            } else {
                return ResponseEntity.ok(ApiResponse.error("Either projectKey or syncProjectId must be provided"));
            }

            if (project == null) {
                return ResponseEntity.ok(ApiResponse.error("Project not found"));
            }

            // Get source and target branch snapshots
            List<ProjectBranchSnapshot> sourceBranches = branchSnapshotService.getBranchSnapshots(project.getId(), "source");
            List<ProjectBranchSnapshot> targetBranches = branchSnapshotService.getBranchSnapshots(project.getId(), "target");

            // Build comparison result
            BranchComparisonResult result = compareBranches(project, sourceBranches, targetBranches);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Query project branches failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Compare source and target branches
     */
    private BranchComparisonResult compareBranches(
            SyncProject project,
            List<ProjectBranchSnapshot> sourceBranches,
            List<ProjectBranchSnapshot> targetBranches) {

        BranchComparisonResult result = new BranchComparisonResult();
        result.setProjectKey(project.getProjectKey());
        result.setSyncProjectId(project.getId());
        result.setSourceBranchCount(sourceBranches.size());
        result.setTargetBranchCount(targetBranches.size());

        // Build maps for quick lookup
        Map<String, ProjectBranchSnapshot> sourceMap = sourceBranches.stream()
                .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));
        Map<String, ProjectBranchSnapshot> targetMap = targetBranches.stream()
                .collect(Collectors.toMap(ProjectBranchSnapshot::getBranchName, b -> b));

        List<BranchInfo> branches = new ArrayList<>();

        // Process source branches
        for (ProjectBranchSnapshot source : sourceBranches) {
            ProjectBranchSnapshot target = targetMap.get(source.getBranchName());
            BranchInfo info = new BranchInfo();
            info.setBranchName(source.getBranchName());
            info.setSourceCommitSha(source.getCommitSha());
            info.setSourceCommitMessage(source.getCommitMessage());
            info.setSourceCommitAuthor(source.getCommitAuthor());
            info.setSourceCommittedAt(source.getCommittedAt());
            info.setIsDefault(source.getIsDefault());
            info.setIsProtected(source.getIsProtected());

            if (target != null) {
                info.setTargetCommitSha(target.getCommitSha());
                info.setTargetCommitMessage(target.getCommitMessage());
                info.setTargetCommitAuthor(target.getCommitAuthor());
                info.setTargetCommittedAt(target.getCommittedAt());

                // Determine status
                if (source.getCommitSha() != null && source.getCommitSha().equals(target.getCommitSha())) {
                    info.setStatus("synced");
                } else {
                    info.setStatus("outdated");
                }
            } else {
                info.setStatus("missing_in_target");
            }

            branches.add(info);
        }

        // Find branches only in target
        for (ProjectBranchSnapshot target : targetBranches) {
            if (!sourceMap.containsKey(target.getBranchName())) {
                BranchInfo info = new BranchInfo();
                info.setBranchName(target.getBranchName());
                info.setTargetCommitSha(target.getCommitSha());
                info.setTargetCommitMessage(target.getCommitMessage());
                info.setTargetCommitAuthor(target.getCommitAuthor());
                info.setTargetCommittedAt(target.getCommittedAt());
                info.setStatus("extra_in_target");
                branches.add(info);
            }
        }

        // Sort branches: default first, then alphabetically
        branches.sort((a, b) -> {
            if (Boolean.TRUE.equals(a.getIsDefault()) && !Boolean.TRUE.equals(b.getIsDefault())) {
                return -1;
            } else if (!Boolean.TRUE.equals(a.getIsDefault()) && Boolean.TRUE.equals(b.getIsDefault())) {
                return 1;
            }
            return a.getBranchName().compareTo(b.getBranchName());
        });

        result.setBranches(branches);

        // Count differences
        long syncedCount = branches.stream().filter(b -> "synced".equals(b.getStatus())).count();
        long outdatedCount = branches.stream().filter(b -> "outdated".equals(b.getStatus())).count();
        long missingCount = branches.stream().filter(b -> "missing_in_target".equals(b.getStatus())).count();
        long extraCount = branches.stream().filter(b -> "extra_in_target".equals(b.getStatus())).count();

        result.setSyncedCount((int) syncedCount);
        result.setOutdatedCount((int) outdatedCount);
        result.setMissingInTargetCount((int) missingCount);
        result.setExtraInTargetCount((int) extraCount);

        return result;
    }

    /**
     * Branch comparison result
     */
    @Data
    public static class BranchComparisonResult {
        private String projectKey;
        private Long syncProjectId;
        private Integer sourceBranchCount;
        private Integer targetBranchCount;
        private Integer syncedCount;
        private Integer outdatedCount;
        private Integer missingInTargetCount;
        private Integer extraInTargetCount;
        private List<BranchInfo> branches;
    }

    /**
     * Branch info
     */
    @Data
    public static class BranchInfo {
        private String branchName;
        private String status; // synced, outdated, missing_in_target, extra_in_target
        private Boolean isDefault;
        private Boolean isProtected;

        // Source branch info
        private String sourceCommitSha;
        private String sourceCommitMessage;
        private String sourceCommitAuthor;
        private LocalDateTime sourceCommittedAt;

        // Target branch info
        private String targetCommitSha;
        private String targetCommitMessage;
        private String targetCommitAuthor;
        private LocalDateTime targetCommittedAt;
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
