package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sync Event Controller
 * <p>
 * REST API for sync events and statistics
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/sync/events")
public class SyncEventController {

    private final SyncEventMapper syncEventMapper;
    private final SyncProjectMapper syncProjectMapper;

    public SyncEventController(SyncEventMapper syncEventMapper, SyncProjectMapper syncProjectMapper) {
        this.syncEventMapper = syncEventMapper;
        this.syncProjectMapper = syncProjectMapper;
    }

    /**
     * Get event list with filters
     *
     * GET /api/sync/events?projectId=&eventType=&status=&startDate=&endDate=&search=&page=1&size=20
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<EventListItem>>> getEvents(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("Query events - projectId: {}, eventType: {}, status: {}, startDate: {}, endDate: {}, search: {}, page: {}, size: {}",
                projectId, eventType, status, startDate, endDate, search, page, size);

        try {
            // Build query wrapper
            QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();

            // Filter by projectId
            if (projectId != null) {
                queryWrapper.eq("sync_project_id", projectId);
            }

            // Filter by eventType
            if (eventType != null && !eventType.isEmpty()) {
                queryWrapper.eq("event_type", eventType);
            }

            // Filter by status
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("status", status);
            }

            // Filter by date range
            if (startDate != null) {
                queryWrapper.ge("event_time", startDate.atStartOfDay());
            }
            if (endDate != null) {
                queryWrapper.le("event_time", endDate.atTime(LocalTime.MAX));
            }

            // Search in error_message or branch_name
            if (search != null && !search.isEmpty()) {
                queryWrapper.and(wrapper -> wrapper
                        .like("error_message", search)
                        .or()
                        .like("branch_name", search));
            }

            // Order by event_time desc
            queryWrapper.orderByDesc("event_time");

            // Pagination
            Page<SyncEvent> pageRequest = new Page<>(page, size);
            IPage<SyncEvent> pageResult = syncEventMapper.selectPage(pageRequest, queryWrapper);

            // Get all project IDs to fetch projectKeys
            List<Long> syncProjectIds = pageResult.getRecords().stream()
                    .map(SyncEvent::getSyncProjectId)
                    .distinct()
                    .collect(Collectors.toList());

            // Fetch project keys in batch
            Map<Long, String> projectKeyMap = Map.of();
            if (!syncProjectIds.isEmpty()) {
                QueryWrapper<SyncProject> projectQuery = new QueryWrapper<>();
                projectQuery.in("id", syncProjectIds);
                List<SyncProject> projects = syncProjectMapper.selectList(projectQuery);
                projectKeyMap = projects.stream()
                        .collect(Collectors.toMap(SyncProject::getId, SyncProject::getProjectKey));
            }

            // Build result items
            Map<Long, String> finalProjectKeyMap = projectKeyMap;
            List<EventListItem> items = pageResult.getRecords().stream()
                    .map(event -> {
                        EventListItem item = new EventListItem();
                        item.setId(event.getId());
                        item.setSyncProjectId(event.getSyncProjectId());
                        item.setProjectKey(finalProjectKeyMap.getOrDefault(event.getSyncProjectId(), ""));
                        item.setEventType(event.getEventType());
                        item.setStatus(event.getStatus());

                        // Build message from available fields
                        String message = buildEventMessage(event);
                        item.setMessage(message);

                        item.setDurationMs(event.getDurationSeconds() != null ? event.getDurationSeconds() * 1000L : null);
                        item.setCreatedAt(event.getEventTime());
                        return item;
                    })
                    .collect(Collectors.toList());

            PageResult<EventListItem> result = new PageResult<>();
            result.setItems(items);
            result.setTotal((int) pageResult.getTotal());
            result.setPage(page);
            result.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Query events failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get event details
     *
     * GET /api/sync/events/{id}/details
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<ApiResponse<EventDetails>> getEventDetails(@PathVariable Long id) {
        log.info("Query event details - id: {}", id);

        try {
            SyncEvent event = syncEventMapper.selectById(id);
            if (event == null) {
                return ResponseEntity.ok(ApiResponse.error("Event not found"));
            }

            EventDetails details = new EventDetails();

            // Basic event info
            EventBasicInfo basicInfo = new EventBasicInfo();
            basicInfo.setId(event.getId());
            basicInfo.setSyncProjectId(event.getSyncProjectId());
            basicInfo.setEventType(event.getEventType());
            basicInfo.setEventSource(event.getEventSource());
            basicInfo.setStatus(event.getStatus());
            basicInfo.setEventTime(event.getEventTime());
            basicInfo.setDurationSeconds(event.getDurationSeconds());

            // Get project key
            if (event.getSyncProjectId() != null) {
                SyncProject project = syncProjectMapper.selectById(event.getSyncProjectId());
                if (project != null) {
                    basicInfo.setProjectKey(project.getProjectKey());
                }
            }

            details.setEvent(basicInfo);
            details.setDetails(event.getEventData());

            return ResponseEntity.ok(ApiResponse.success(details));
        } catch (Exception e) {
            log.error("Query event details failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get event statistics
     *
     * GET /api/sync/events/stats?date=2025-12-23
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<EventStats>> getEventStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // Default to today if no date specified
        if (date == null) {
            date = LocalDate.now();
        }

        log.info("Query event stats - date: {}", date);

        try {
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

            QueryWrapper<SyncEvent> queryWrapper = new QueryWrapper<>();
            queryWrapper.between("event_time", startOfDay, endOfDay);

            List<SyncEvent> events = syncEventMapper.selectList(queryWrapper);

            EventStats stats = new EventStats();
            stats.setTotalEvents(events.size());
            stats.setSuccessEvents((int) events.stream().filter(e -> "success".equals(e.getStatus())).count());
            stats.setFailedEvents((int) events.stream().filter(e -> "failed".equals(e.getStatus())).count());

            // Calculate average duration (only for completed events)
            List<SyncEvent> completedEvents = events.stream()
                    .filter(e -> e.getDurationSeconds() != null)
                    .toList();

            if (!completedEvents.isEmpty()) {
                double avgDurationSeconds = completedEvents.stream()
                        .mapToInt(SyncEvent::getDurationSeconds)
                        .average()
                        .orElse(0.0);
                stats.setAvgDurationMs((long) (avgDurationSeconds * 1000));
            } else {
                stats.setAvgDurationMs(0L);
            }

            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Query event stats failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Build event message from SyncEvent
     */
    private String buildEventMessage(SyncEvent event) {
        if (event.getErrorMessage() != null && !event.getErrorMessage().isEmpty()) {
            return event.getErrorMessage();
        }

        StringBuilder message = new StringBuilder();
        if (event.getBranchName() != null) {
            message.append("分支 ").append(event.getBranchName());
        }
        if (event.getCommitSha() != null) {
            message.append(" (").append(event.getCommitSha().substring(0, Math.min(8, event.getCommitSha().length()))).append(")");
        }

        if (message.length() == 0) {
            message.append(event.getEventType());
        }

        return message.toString();
    }

    /**
     * Event details
     */
    @Data
    public static class EventDetails {
        private EventBasicInfo event;
        private Map<String, Object> details;
    }

    /**
     * Event basic info
     */
    @Data
    public static class EventBasicInfo {
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
     * Event list item
     */
    @Data
    public static class EventListItem {
        private Long id;
        private Long syncProjectId;
        private String projectKey;
        private String eventType;
        private String status;
        private String message;
        private Long durationMs;
        private LocalDateTime createdAt;
    }

    /**
     * Page result
     */
    @Data
    public static class PageResult<T> {
        private List<T> items;
        private Integer total;
        private Integer page;
        private Integer size;
    }

    /**
     * Event statistics
     */
    @Data
    public static class EventStats {
        private Integer totalEvents;
        private Integer successEvents;
        private Integer failedEvents;
        private Long avgDurationMs;
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
