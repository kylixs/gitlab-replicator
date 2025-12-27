package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.ProjectListService;
import com.gitlab.mirror.server.controller.dto.ProjectListDTO;
import com.gitlab.mirror.server.model.SyncStatistics;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

            // Sort by delay and take top N (include projects with 0 delay, only filter null)
            List<DelayedProject> topDelayed = projectDTOs.stream()
                    .filter(dto -> dto.getDelaySeconds() != null)
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
     * Get today's sync statistics
     *
     * GET /api/dashboard/today-stats
     */
    @GetMapping("/today-stats")
    public ResponseEntity<ApiResponse<TodaySyncStats>> getTodayStats() {
        log.info("Query today's sync statistics");

        try {
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();

            // Query today's sync events
            QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
            queryWrapper.ge("event_time", todayStart);
            queryWrapper.eq("event_type", "sync_finished");

            List<SyncEvent> todayEvents = syncEventMapper.selectList(queryWrapper);

            // Calculate statistics
            long totalSyncs = todayEvents.size();
            long successSyncs = todayEvents.stream()
                    .filter(e -> "success".equalsIgnoreCase(e.getStatus()))
                    .count();
            long failedSyncs = todayEvents.stream()
                    .filter(e -> "failed".equalsIgnoreCase(e.getStatus()))
                    .count();

            // Calculate total branch changes
            int totalBranchChanges = todayEvents.stream()
                    .filter(e -> e.getStatistics() != null)
                    .mapToInt(e -> {
                        SyncStatistics stats = e.getStatistics();
                        int changes = 0;
                        if (stats.getBranchesCreated() != null) changes += stats.getBranchesCreated();
                        if (stats.getBranchesUpdated() != null) changes += stats.getBranchesUpdated();
                        if (stats.getBranchesDeleted() != null) changes += stats.getBranchesDeleted();
                        return changes;
                    })
                    .sum();

            TodaySyncStats stats = new TodaySyncStats();
            stats.setTotalSyncs((int) totalSyncs);
            stats.setSuccessSyncs((int) successSyncs);
            stats.setFailedSyncs((int) failedSyncs);
            stats.setTotalBranchChanges(totalBranchChanges);

            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Query today's sync statistics failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get sync event trends
     *
     * GET /api/dashboard/trend?range=7d or range=24h
     */
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<TrendData>> getTrend(
            @RequestParam(defaultValue = "7d") String range) {
        log.info("Query trend data - range: {}", range);

        try {
            if ("24h".equals(range)) {
                return ResponseEntity.ok(ApiResponse.success(getTrend24h()));
            } else {
                return ResponseEntity.ok(ApiResponse.success(getTrend7d()));
            }
        } catch (Exception e) {
            log.error("Query trend data failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    private TrendData getTrend7d() {
        // Use SQL aggregation to get daily statistics
        List<Map<String, Object>> dailyStats = syncEventMapper.getDailyTrend7d();

        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.List<Integer> totalSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> successSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> failedSyncs = new java.util.ArrayList<>();

        for (Map<String, Object> stat : dailyStats) {
            java.sql.Date date = (java.sql.Date) stat.get("date");
            Number total = (Number) stat.get("total");
            Number success = (Number) stat.get("success");
            Number failed = (Number) stat.get("failed");

            dates.add(date.toString());
            totalSyncs.add(total != null ? total.intValue() : 0);
            successSyncs.add(success != null ? success.intValue() : 0);
            failedSyncs.add(failed != null ? failed.intValue() : 0);
        }

        TrendData trendData = new TrendData();
        trendData.setDates(dates);
        trendData.setTotalSyncs(totalSyncs);
        trendData.setSuccessSyncs(successSyncs);
        trendData.setFailedSyncs(failedSyncs);

        return trendData;
    }

    private TrendData getTrend24h() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentHourStart = now.withMinute(0).withSecond(0).withNano(0);

        // Use SQL aggregation to get hourly statistics
        List<Map<String, Object>> hourlyStats = syncEventMapper.getHourlyTrend24h(currentHourStart);

        java.util.List<String> hours = new java.util.ArrayList<>();
        java.util.List<Integer> totalSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> successSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> failedSyncs = new java.util.ArrayList<>();

        for (Map<String, Object> stat : hourlyStats) {
            Integer hour = (Integer) stat.get("hour");
            Number total = (Number) stat.get("total");
            Number success = (Number) stat.get("success");
            Number failed = (Number) stat.get("failed");

            hours.add(String.valueOf(hour));
            totalSyncs.add(total != null ? total.intValue() : 0);
            successSyncs.add(success != null ? success.intValue() : 0);
            failedSyncs.add(failed != null ? failed.intValue() : 0);
        }

        TrendData trendData = new TrendData();
        trendData.setDates(hours);
        trendData.setTotalSyncs(totalSyncs);
        trendData.setSuccessSyncs(successSyncs);
        trendData.setFailedSyncs(failedSyncs);

        return trendData;
    }

    /**
     * Get event type trends
     *
     * GET /api/dashboard/event-type-trend?range=7d or range=24h
     */
    @GetMapping("/event-type-trend")
    public ResponseEntity<ApiResponse<EventTypeTrend>> getEventTypeTrend(
            @RequestParam(defaultValue = "7d") String range) {
        log.info("Query event type trend - range: {}", range);

        try {
            if ("24h".equals(range)) {
                return ResponseEntity.ok(ApiResponse.success(getEventTypeTrend24h()));
            } else {
                return ResponseEntity.ok(ApiResponse.success(getEventTypeTrend7d()));
            }
        } catch (Exception e) {
            log.error("Query event type trend failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    private EventTypeTrend getEventTypeTrend7d() {
        // Use SQL aggregation to get daily event type statistics
        List<Map<String, Object>> dailyTypeStats = syncEventMapper.getDailyEventTypeTrend7d();

        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<Integer>> typeData = new java.util.HashMap<>();

        // First pass: collect all unique dates and event types
        java.util.Set<String> uniqueDates = new java.util.LinkedHashSet<>();
        java.util.Set<String> uniqueTypes = new java.util.LinkedHashSet<>();

        for (Map<String, Object> stat : dailyTypeStats) {
            java.sql.Date date = (java.sql.Date) stat.get("date");
            String eventType = (String) stat.get("event_type");

            if (date != null) {
                uniqueDates.add(date.toString());
            }
            if (eventType != null && !eventType.isEmpty()) {
                uniqueTypes.add(eventType);
            }
        }

        // Initialize typeData for all event types
        for (String type : uniqueTypes) {
            typeData.put(type, new java.util.ArrayList<>());
        }

        // Build date-type-count map from query results
        java.util.Map<String, Integer> dateTypeCountMap = new java.util.HashMap<>();
        for (Map<String, Object> stat : dailyTypeStats) {
            java.sql.Date date = (java.sql.Date) stat.get("date");
            String eventType = (String) stat.get("event_type");
            Number count = (Number) stat.get("count");

            if (date != null && eventType != null && !eventType.isEmpty()) {
                String key = date.toString() + ":" + eventType;
                dateTypeCountMap.put(key, count != null ? count.intValue() : 0);
            }
        }

        // Fill data for each date in order
        for (String date : uniqueDates) {
            dates.add(date);

            for (String eventType : uniqueTypes) {
                String key = date + ":" + eventType;
                int count = dateTypeCountMap.getOrDefault(key, 0);
                typeData.get(eventType).add(count);
            }
        }

        EventTypeTrend trend = new EventTypeTrend();
        trend.setDates(dates);
        trend.setTypeData(typeData);

        return trend;
    }

    private EventTypeTrend getEventTypeTrend24h() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentHourStart = now.withMinute(0).withSecond(0).withNano(0);

        // Use SQL aggregation to get hourly event type statistics
        List<Map<String, Object>> hourlyTypeStats = syncEventMapper.getHourlyEventTypeTrend24h(currentHourStart);

        java.util.List<String> hours = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<Integer>> typeData = new java.util.HashMap<>();

        // First pass: collect all unique hours and event types
        java.util.Set<Integer> uniqueHours = new java.util.LinkedHashSet<>();
        java.util.Set<String> uniqueTypes = new java.util.LinkedHashSet<>();

        for (Map<String, Object> stat : hourlyTypeStats) {
            Integer hour = (Integer) stat.get("hour");
            String eventType = (String) stat.get("event_type");

            if (hour != null) {
                uniqueHours.add(hour);
            }
            if (eventType != null && !eventType.isEmpty()) {
                uniqueTypes.add(eventType);
            }
        }

        // Initialize typeData for all event types
        for (String type : uniqueTypes) {
            typeData.put(type, new java.util.ArrayList<>());
        }

        // Build hour-type-count map from query results
        java.util.Map<String, Integer> hourTypeCountMap = new java.util.HashMap<>();
        for (Map<String, Object> stat : hourlyTypeStats) {
            Integer hour = (Integer) stat.get("hour");
            String eventType = (String) stat.get("event_type");
            Number count = (Number) stat.get("count");

            if (hour != null && eventType != null && !eventType.isEmpty()) {
                String key = hour + ":" + eventType;
                hourTypeCountMap.put(key, count != null ? count.intValue() : 0);
            }
        }

        // Fill data for each hour in order
        for (Integer hour : uniqueHours) {
            hours.add(String.valueOf(hour));

            for (String eventType : uniqueTypes) {
                String key = hour + ":" + eventType;
                int count = hourTypeCountMap.getOrDefault(key, 0);
                typeData.get(eventType).add(count);
            }
        }

        EventTypeTrend trend = new EventTypeTrend();
        trend.setDates(hours);
        trend.setTypeData(typeData);

        return trend;
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
                        recentEvent.setStatistics(event.getStatistics());
                        recentEvent.setErrorMessage(event.getErrorMessage());

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
     * Today's sync statistics
     */
    @Data
    public static class TodaySyncStats {
        private Integer totalSyncs;
        private Integer successSyncs;
        private Integer failedSyncs;
        private Integer totalBranchChanges;
    }

    /**
     * Trend data (for both 7d and 24h)
     */
    @Data
    public static class TrendData {
        private java.util.List<String> dates;  // dates for 7d, hours for 24h
        private java.util.List<Integer> totalSyncs;
        private java.util.List<Integer> successSyncs;
        private java.util.List<Integer> failedSyncs;
    }

    /**
     * Event type trend data
     */
    @Data
    public static class EventTypeTrend {
        private java.util.List<String> dates;  // dates for 7d, hours for 24h
        private java.util.Map<String, java.util.List<Integer>> typeData;  // eventType -> counts
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
        private SyncStatistics statistics;
        private String errorMessage;
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
