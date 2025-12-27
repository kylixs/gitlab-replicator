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
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("event_time", sevenDaysAgo);
        queryWrapper.eq("event_type", "sync_finished");

        List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

        // Group by date
        java.util.Map<String, java.util.List<SyncEvent>> eventsByDate = events.stream()
                .collect(Collectors.groupingBy(e -> e.getEventTime().toLocalDate().toString()));

        // Build trend data for last 7 days
        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.List<Integer> totalSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> successSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> failedSyncs = new java.util.ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            String dateStr = date.toLocalDate().toString();
            dates.add(dateStr);

            java.util.List<SyncEvent> dayEvents = eventsByDate.getOrDefault(dateStr, new java.util.ArrayList<>());
            int total = dayEvents.size();
            int success = (int) dayEvents.stream().filter(e -> "success".equalsIgnoreCase(e.getStatus())).count();
            int failed = (int) dayEvents.stream().filter(e -> "failed".equalsIgnoreCase(e.getStatus())).count();

            totalSyncs.add(total);
            successSyncs.add(success);
            failedSyncs.add(failed);
        }

        TrendData trendData = new TrendData();
        trendData.setDates(dates);
        trendData.setTotalSyncs(totalSyncs);
        trendData.setSuccessSyncs(successSyncs);
        trendData.setFailedSyncs(failedSyncs);

        return trendData;
    }

    private TrendData getTrend24h() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("event_time", twentyFourHoursAgo);
        queryWrapper.eq("event_type", "sync_finished");

        List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

        // Group by hour
        java.util.Map<Integer, java.util.List<SyncEvent>> eventsByHour = events.stream()
                .collect(Collectors.groupingBy(e -> e.getEventTime().getHour()));

        // Build trend data for last 24 hours
        java.util.List<String> hours = new java.util.ArrayList<>();
        java.util.List<Integer> totalSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> successSyncs = new java.util.ArrayList<>();
        java.util.List<Integer> failedSyncs = new java.util.ArrayList<>();

        for (int i = 23; i >= 0; i--) {
            LocalDateTime hourTime = LocalDateTime.now().minusHours(i);
            int hour = hourTime.getHour();
            hours.add(String.valueOf(hour));

            java.util.List<SyncEvent> hourEvents = eventsByHour.getOrDefault(hour, new java.util.ArrayList<>());
            // Filter events to only those within this specific hour of the last 24h
            hourEvents = hourEvents.stream()
                    .filter(e -> e.getEventTime().isAfter(hourTime.minusMinutes(30)) &&
                                 e.getEventTime().isBefore(hourTime.plusMinutes(30)))
                    .collect(Collectors.toList());

            int total = hourEvents.size();
            int success = (int) hourEvents.stream().filter(e -> "success".equalsIgnoreCase(e.getStatus())).count();
            int failed = (int) hourEvents.stream().filter(e -> "failed".equalsIgnoreCase(e.getStatus())).count();

            totalSyncs.add(total);
            successSyncs.add(success);
            failedSyncs.add(failed);
        }

        TrendData trendData = new TrendData();
        trendData.setDates(hours);  // Reuse dates field for hours
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
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("event_time", sevenDaysAgo);

        List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

        // Group by date and event type
        java.util.Map<String, java.util.Map<String, Long>> eventsByDateAndType = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getEventTime().toLocalDate().toString(),
                        Collectors.groupingBy(
                                SyncEvent::getEventType,
                                Collectors.counting()
                        )
                ));

        // Collect all event types from all 7 days
        java.util.Set<String> allEventTypes = eventsByDateAndType.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());

        // Build trend data for last 7 days
        java.util.List<String> dates = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<Integer>> typeData = new java.util.HashMap<>();

        // Initialize typeData for all event types
        for (String eventType : allEventTypes) {
            typeData.put(eventType, new java.util.ArrayList<>());
        }

        // Fill in data for each day
        for (int i = 6; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            String dateStr = date.toLocalDate().toString();
            dates.add(dateStr);

            java.util.Map<String, Long> dayTypeCount = eventsByDateAndType.getOrDefault(dateStr, new java.util.HashMap<>());

            // Fill in counts for this day for all event types
            for (String eventType : allEventTypes) {
                int count = dayTypeCount.getOrDefault(eventType, 0L).intValue();
                typeData.get(eventType).add(count);
            }
        }

        EventTypeTrend trend = new EventTypeTrend();
        trend.setDates(dates);
        trend.setTypeData(typeData);

        return trend;
    }

    private EventTypeTrend getEventTypeTrend24h() {
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("event_time", twentyFourHoursAgo);

        List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

        // Group by hour and event type
        java.util.Map<Integer, java.util.Map<String, Long>> eventsByHourAndType = new java.util.HashMap<>();

        for (SyncEvent event : events) {
            int hour = event.getEventTime().getHour();
            eventsByHourAndType.putIfAbsent(hour, new java.util.HashMap<>());
            String eventType = event.getEventType();
            eventsByHourAndType.get(hour).merge(eventType, 1L, Long::sum);
        }

        // Build trend data for last 24 hours
        java.util.List<String> hours = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<Integer>> typeData = new java.util.HashMap<>();

        // Collect all event types
        java.util.Set<String> allTypes = eventsByHourAndType.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toSet());

        for (String type : allTypes) {
            typeData.put(type, new java.util.ArrayList<>());
        }

        for (int i = 23; i >= 0; i--) {
            LocalDateTime hourTime = LocalDateTime.now().minusHours(i);
            int hour = hourTime.getHour();
            hours.add(String.valueOf(hour));

            java.util.Map<String, Long> hourTypeCount = eventsByHourAndType.getOrDefault(hour, new java.util.HashMap<>());

            // Fill in counts for this hour
            for (String eventType : typeData.keySet()) {
                int count = hourTypeCount.getOrDefault(eventType, 0L).intValue();
                typeData.get(eventType).add(count);
            }
        }

        EventTypeTrend trend = new EventTypeTrend();
        trend.setDates(hours);  // Reuse dates field for hours
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
