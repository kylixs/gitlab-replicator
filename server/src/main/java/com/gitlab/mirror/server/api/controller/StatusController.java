package com.gitlab.mirror.server.api.controller;

import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.service.EventManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Status API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class StatusController {

    private final EventManagementService eventManagementService;

    public StatusController(EventManagementService eventManagementService) {
        this.eventManagementService = eventManagementService;
    }

    /**
     * Get service status
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "running");
        status.put("timestamp", LocalDateTime.now());
        status.put("version", "1.0.0-SNAPSHOT");

        return ApiResponse.success(status);
    }

    /**
     * Reload configuration
     */
    @PostMapping("/reload")
    public ApiResponse<Void> reloadConfiguration() {
        log.info("Configuration reload requested");
        // In MVP, we just acknowledge the request
        // In production, implement actual reload logic
        return ApiResponse.success(null, "Configuration reloaded");
    }

    /**
     * Get overall statistics
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get event statistics
        Map<String, Object> eventStats = eventManagementService.getEventStatistics();
        stats.put("events", eventStats);

        // Get event counts by type
        Map<String, Long> eventsByType = eventManagementService.countEventsByType();
        stats.put("events_by_type", eventsByType);

        // Get event counts by status
        Map<String, Long> eventsByStatus = eventManagementService.countEventsByStatus();
        stats.put("events_by_status", eventsByStatus);

        // Get average sync delay
        Double avgDelay = eventManagementService.getAverageSyncDelay();
        stats.put("avg_sync_delay_seconds", avgDelay);

        return ApiResponse.success(stats);
    }
}
