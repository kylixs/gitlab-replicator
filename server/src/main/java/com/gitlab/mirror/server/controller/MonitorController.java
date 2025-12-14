package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.SyncMonitorService;
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
 * Monitor Controller
 * <p>
 * REST API for monitoring and alerts management.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final SyncMonitorService syncMonitorService;
    private final MonitorAlertMapper monitorAlertMapper;
    private final SyncProjectMapper syncProjectMapper;

    public MonitorController(
            SyncMonitorService syncMonitorService,
            MonitorAlertMapper monitorAlertMapper,
            SyncProjectMapper syncProjectMapper) {
        this.syncMonitorService = syncMonitorService;
        this.monitorAlertMapper = monitorAlertMapper;
        this.syncProjectMapper = syncProjectMapper;
    }

    /**
     * Get monitoring status overview
     *
     * GET /api/monitor/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MonitorStatus>> getStatus() {
        log.info("Query monitor status");

        try {
            // Get project statistics
            List<SyncProject> allProjects = syncProjectMapper.selectList(null);

            Map<String, Integer> statusCount = new HashMap<>();
            statusCount.put("total", allProjects.size());
            statusCount.put("synced", 0);
            statusCount.put("outdated", 0);
            statusCount.put("failed", 0);
            statusCount.put("inconsistent", 0);

            for (SyncProject project : allProjects) {
                String status = project.getSyncStatus();
                if (status != null) {
                    statusCount.merge(status, 1, Integer::sum);
                }
            }

            // Get alert statistics
            QueryWrapper<MonitorAlert> alertQuery = new QueryWrapper<>();
            alertQuery.eq("status", MonitorAlert.Status.ACTIVE);
            List<MonitorAlert> activeAlerts = monitorAlertMapper.selectList(alertQuery);

            Map<String, Integer> alertCount = new HashMap<>();
            alertCount.put("active", activeAlerts.size());
            alertCount.put("critical", 0);
            alertCount.put("high", 0);
            alertCount.put("medium", 0);
            alertCount.put("low", 0);

            for (MonitorAlert alert : activeAlerts) {
                String severity = alert.getSeverity();
                if (severity != null) {
                    alertCount.merge(severity, 1, Integer::sum);
                }
            }

            MonitorStatus status = new MonitorStatus();
            status.setSummary(statusCount);
            status.setAlerts(alertCount);

            return ResponseEntity.ok(ApiResponse.success(status));
        } catch (Exception e) {
            log.error("Query monitor status failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Get alert list with pagination
     *
     * GET /api/monitor/alerts?severity=critical&status=active&page=1&size=20
     */
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<PageResult<MonitorAlert>>> getAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        log.info("Query alerts - severity: {}, status: {}, page: {}, size: {}",
                severity, status, page, size);

        try {
            QueryWrapper<MonitorAlert> queryWrapper = new QueryWrapper<>();

            if (severity != null && !severity.isEmpty()) {
                queryWrapper.eq("severity", severity);
            }

            if (status != null && !status.isEmpty()) {
                queryWrapper.eq("status", status);
            }

            queryWrapper.orderByDesc("triggered_at");

            Page<MonitorAlert> pageQuery = new Page<>(page, size);
            Page<MonitorAlert> result = monitorAlertMapper.selectPage(pageQuery, queryWrapper);

            PageResult<MonitorAlert> pageResult = new PageResult<>();
            pageResult.setItems(result.getRecords());
            pageResult.setTotal(result.getTotal());
            pageResult.setPage(page);
            pageResult.setSize(size);

            return ResponseEntity.ok(ApiResponse.success(pageResult));
        } catch (Exception e) {
            log.error("Query alerts failed", e);
            return ResponseEntity.ok(ApiResponse.error("Query failed: " + e.getMessage()));
        }
    }

    /**
     * Resolve alert
     *
     * POST /api/monitor/alerts/{id}/resolve
     */
    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<ApiResponse<Void>> resolveAlert(@PathVariable Long id) {
        log.info("Resolve alert: {}", id);

        try {
            boolean success = syncMonitorService.resolveAlert(id);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(null));
            } else {
                return ResponseEntity.ok(ApiResponse.error("Alert not found or already resolved"));
            }
        } catch (Exception e) {
            log.error("Resolve alert failed", e);
            return ResponseEntity.ok(ApiResponse.error("Resolve failed: " + e.getMessage()));
        }
    }

    /**
     * Mute alert
     *
     * POST /api/monitor/alerts/{id}/mute?duration=60
     */
    @PostMapping("/alerts/{id}/mute")
    public ResponseEntity<ApiResponse<Void>> muteAlert(
            @PathVariable Long id,
            @RequestParam(defaultValue = "60") Integer duration) {
        log.info("Mute alert: {} for {} minutes", id, duration);

        try {
            boolean success = syncMonitorService.muteAlert(id, duration);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(null));
            } else {
                return ResponseEntity.ok(ApiResponse.error("Alert not found"));
            }
        } catch (Exception e) {
            log.error("Mute alert failed", e);
            return ResponseEntity.ok(ApiResponse.error("Mute failed: " + e.getMessage()));
        }
    }

    /**
     * Monitor Status DTO
     */
    @Data
    public static class MonitorStatus {
        private Map<String, Integer> summary;
        private Map<String, Integer> alerts;
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
