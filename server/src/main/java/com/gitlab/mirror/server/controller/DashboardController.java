package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.ProjectListService;
import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dashboard Controller
 * <p>
 * REST API for dashboard statistics and monitoring
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SyncProjectMapper syncProjectMapper;
    private final SyncEventMapper syncEventMapper;
    private final ProjectListService projectListService;

    public DashboardController(
            SyncProjectMapper syncProjectMapper,
            SyncEventMapper syncEventMapper,
            ProjectListService projectListService) {
        this.syncProjectMapper = syncProjectMapper;
        this.syncEventMapper = syncEventMapper;
        this.projectListService = projectListService;
    }

    /**
     * Get dashboard statistics
     *
     * GET /api/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats() {
        log.info("Query dashboard stats");

        try {
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            DashboardStats stats = new DashboardStats();
            stats.setTotalProjects(allProjects.size());
            stats.setSyncedProjects((int) allProjects.stream()
                    .filter(p -> "synced".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            stats.setSyncingProjects((int) allProjects.stream()
                    .filter(p -> "syncing".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            stats.setPausedProjects((int) allProjects.stream()
                    .filter(p -> "paused".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            stats.setFailedProjects((int) allProjects.stream()
                    .filter(p -> "failed".equalsIgnoreCase(p.getSyncStatus()))
                    .count());

            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Query dashboard stats failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get status distribution
     *
     * GET /api/dashboard/status-distribution
     */
    @GetMapping("/status-distribution")
    public ResponseEntity<ApiResponse<StatusDistribution>> getStatusDistribution() {
        log.info("Query status distribution");

        try {
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            StatusDistribution distribution = new StatusDistribution();
            distribution.setSynced((int) allProjects.stream()
                    .filter(p -> "synced".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            distribution.setSyncing((int) allProjects.stream()
                    .filter(p -> "syncing".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            distribution.setPending((int) allProjects.stream()
                    .filter(p -> "pending".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            distribution.setPaused((int) allProjects.stream()
                    .filter(p -> "paused".equalsIgnoreCase(p.getSyncStatus()))
                    .count());
            distribution.setFailed((int) allProjects.stream()
                    .filter(p -> "failed".equalsIgnoreCase(p.getSyncStatus()))
                    .count());

            return ResponseEntity.ok(ApiResponse.success(distribution));
        } catch (Exception e) {
            log.error("Query status distribution failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get top delayed projects
     *
     * GET /api/dashboard/top-delayed-projects?limit=10
     */
    @GetMapping("/top-delayed-projects")
    public ResponseEntity<ApiResponse<List<DelayedProject>>> getTopDelayedProjects(
            @RequestParam(defaultValue = "10") Integer limit) {
        log.info("Query top delayed projects - limit: {}", limit);

        try {
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            // Build DTOs with delay info
            List<ProjectListDTO> projectDTOs = allProjects.stream()
                    .map(projectListService::buildProjectListDTO)
                    .collect(Collectors.toList());

            // Sort by delay and take top N
            List<DelayedProject> topDelayed = projectDTOs.stream()
                    .filter(dto -> dto.getDelaySeconds() != null && dto.getDelaySeconds() > 0)
                    .sorted((a, b) -> Long.compare(b.getDelaySeconds(), a.getDelaySeconds()))
                    .limit(limit)
                    .map(dto -> {
                        DelayedProject delayed = new DelayedProject();
                        delayed.setProjectKey(dto.getProjectKey());
                        delayed.setSyncProjectId(dto.getId());
                        delayed.setDelaySeconds(dto.getDelaySeconds());
                        delayed.setDelayFormatted(dto.getDelayFormatted());
                        delayed.setSyncStatus(dto.getSyncStatus());
                        return delayed;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(topDelayed));
        } catch (Exception e) {
            log.error("Query top delayed projects failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get recent events
     *
     * GET /api/dashboard/recent-events?limit=20
     */
    @GetMapping("/recent-events")
    public ResponseEntity<ApiResponse<List<RecentEvent>>> getRecentEvents(
            @RequestParam(defaultValue = "20") Integer limit) {
        log.info("Query recent events - limit: {}", limit);

        try {
            QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByDesc("event_time");
            queryWrapper.last("LIMIT " + limit);

            List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

            List<RecentEvent> recentEvents = events.stream()
                    .map(event -> {
                        RecentEvent recentEvent = new RecentEvent();
                        recentEvent.setId(event.getId());
                        recentEvent.setSyncProjectId(event.getSyncProjectId());
                        recentEvent.setEventType(event.getEventType());
                        recentEvent.setEventSource(event.getEventSource());
                        recentEvent.setStatus(event.getStatus());
                        recentEvent.setEventTime(event.getEventTime());
                        recentEvent.setDurationSeconds(event.getDurationSeconds());

                        // Get project key
                        if (event.getSyncProjectId() != null) {
                            SyncProject project = syncProjectMapper.selectById(event.getSyncProjectId());
                            if (project != null) {
                                recentEvent.setProjectKey(project.getProjectKey());
                            }
                        }

                        return recentEvent;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(recentEvents));
        } catch (Exception e) {
            log.error("Query recent events failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Dashboard statistics
     */
    @Data
    public static class DashboardStats {
        private Integer totalProjects;
        private Integer syncedProjects;
        private Integer syncingProjects;
        private Integer pausedProjects;
        private Integer failedProjects;
    }

    /**
     * Status distribution
     */
    @Data
    public static class StatusDistribution {
        private Integer synced;
        private Integer syncing;
        private Integer pending;
        private Integer paused;
        private Integer failed;
    }

    /**
     * Delayed project
     */
    @Data
    public static class DelayedProject {
        private String projectKey;
        private Long syncProjectId;
        private Long delaySeconds;
        private String delayFormatted;
        private String syncStatus;
    }

    /**
     * Recent event
     */
    @Data
    public static class RecentEvent {
        private Long id;
        private Long syncProjectId;
        private String projectKey;
        private String eventType;
        private String eventSource;
        private String status;
        private LocalDateTime eventTime;
        private Integer durationSeconds;
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
}
