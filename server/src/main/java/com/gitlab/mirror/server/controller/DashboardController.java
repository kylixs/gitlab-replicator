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
     * Get dashboard statistics (dynamic status counts)
     *
     * GET /api/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DynamicDashboardStats>> getStats() {
        log.info("Query dashboard stats");

        try {
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            // Group by status and count
            java.util.Map<String, Long> statusCounts = allProjects.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getSyncStatus() != null ? p.getSyncStatus() : "unknown",
                            Collectors.counting()
                    ));

            DynamicDashboardStats stats = new DynamicDashboardStats();
            stats.setTotalProjects(allProjects.size());
            stats.setStatusCounts(statusCounts);

            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Query dashboard stats failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get status distribution (dynamic, same as stats for simplicity)
     *
     * GET /api/dashboard/status-distribution
     */
    @GetMapping("/status-distribution")
    public ResponseEntity<ApiResponse<java.util.List<StatusItem>>> getStatusDistribution() {
        log.info("Query status distribution");

        try {
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            // Group by status and count, convert to list for chart display
            java.util.List<StatusItem> distribution = allProjects.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getSyncStatus() != null ? p.getSyncStatus() : "unknown",
                            Collectors.counting()
                    ))
                    .entrySet()
                    .stream()
                    .map(entry -> new StatusItem(entry.getKey(), entry.getValue().intValue()))
                    .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                    .collect(Collectors.toList());

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
     * Dynamic dashboard statistics
     */
    @Data
    public static class DynamicDashboardStats {
        private Integer totalProjects;
        private java.util.Map<String, Long> statusCounts;  // status -> count mapping
    }

    /**
     * Status item for distribution chart
     */
    @Data
    @lombok.AllArgsConstructor
    public static class StatusItem {
        private String status;
        private Integer count;
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
