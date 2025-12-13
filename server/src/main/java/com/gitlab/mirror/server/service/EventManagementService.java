package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event Management Service
 *
 * 负责事件的记录、查询和统计分析
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class EventManagementService {

    private final SyncEventMapper syncEventMapper;
    private final com.gitlab.mirror.server.mapper.SyncProjectMapper syncProjectMapper;

    public EventManagementService(SyncEventMapper syncEventMapper,
                                 com.gitlab.mirror.server.mapper.SyncProjectMapper syncProjectMapper) {
        this.syncEventMapper = syncEventMapper;
        this.syncProjectMapper = syncProjectMapper;
    }

    /**
     * 记录单个事件
     *
     * @param event 事件对象
     * @return 记录的事件
     */
    @Transactional
    public SyncEvent recordEvent(SyncEvent event) {
        // 自动填充时间戳
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }

        // 数据验证
        validateEvent(event);

        syncEventMapper.insert(event);
        log.debug("Event recorded: type={}, source={}, status={}",
                event.getEventType(), event.getEventSource(), event.getStatus());

        return event;
    }

    /**
     * 批量记录事件
     *
     * @param events 事件列表
     * @return 记录的事件数量
     */
    @Transactional
    public int recordEvents(List<SyncEvent> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        for (SyncEvent event : events) {
            // 自动填充时间戳
            if (event.getEventTime() == null) {
                event.setEventTime(now);
            }
            // 数据验证
            validateEvent(event);
        }

        // 批量插入
        int count = 0;
        for (SyncEvent event : events) {
            syncEventMapper.insert(event);
            count++;
        }

        log.info("Batch recorded {} events", count);
        return count;
    }

    /**
     * 按项目ID查询事件
     *
     * @param syncProjectId 同步项目ID
     * @param limit 限制数量
     * @return 事件列表
     */
    public List<SyncEvent> getEventsBySyncProject(Long syncProjectId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 100;
        }
        return syncEventMapper.selectBySyncProjectId(syncProjectId, limit);
    }

    /**
     * 按事件类型查询
     *
     * @param eventType 事件类型
     * @return 事件列表
     */
    public List<SyncEvent> getEventsByType(String eventType) {
        return syncEventMapper.selectByEventType(eventType);
    }

    /**
     * 按状态查询
     *
     * @param status 状态
     * @return 事件列表
     */
    public List<SyncEvent> getEventsByStatus(String status) {
        return syncEventMapper.selectByStatus(status);
    }

    /**
     * 按时间范围查询
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 事件列表
     */
    public List<SyncEvent> getEventsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return syncEventMapper.selectByTimeRange(startTime, endTime);
    }

    /**
     * 多维度分页查询
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param syncProjectId 同步项目ID（可选）
     * @param eventType 事件类型（可选）
     * @param status 状态（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @return 分页结果
     */
    public IPage<SyncEvent> queryEvents(int pageNum, int pageSize,
                                         Long syncProjectId, String eventType, String status,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        Page<SyncEvent> page = new Page<>(pageNum, pageSize);
        return syncEventMapper.selectPageWithFilters(page, syncProjectId, eventType, status, startTime, endTime);
    }

    /**
     * 根据项目ID获取项目路径
     *
     * @param syncProjectId 同步项目ID
     * @return 项目路径
     */
    public String getProjectKey(Long syncProjectId) {
        if (syncProjectId == null) {
            return null;
        }

        com.gitlab.mirror.server.entity.SyncProject project =
            syncProjectMapper.selectById(syncProjectId);

        return project != null ? project.getProjectKey() : null;
    }

    /**
     * 批量获取项目路径
     *
     * @param syncProjectIds 同步项目ID列表
     * @return 项目ID到路径的映射
     */
    public Map<Long, String> getProjectKeys(List<Long> syncProjectIds) {
        Map<Long, String> projectKeyMap = new HashMap<>();

        if (syncProjectIds == null || syncProjectIds.isEmpty()) {
            return projectKeyMap;
        }

        for (Long syncProjectId : syncProjectIds) {
            if (syncProjectId != null) {
                String projectKey = getProjectKey(syncProjectId);
                if (projectKey != null) {
                    projectKeyMap.put(syncProjectId, projectKey);
                }
            }
        }

        return projectKeyMap;
    }

    /**
     * 获取事件统计
     *
     * @return 统计数据
     */
    public Map<String, Object> getEventStatistics() {
        Map<String, Object> stats = syncEventMapper.getEventStatistics();
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("total", 0L);
            stats.put("success_count", 0L);
            stats.put("failed_count", 0L);
            stats.put("avg_duration", 0.0);
        }
        return stats;
    }

    /**
     * 按类型统计事件数量
     *
     * @return 类型统计
     */
    public Map<String, Long> countEventsByType() {
        List<Map<String, Object>> results = syncEventMapper.countByEventType();
        Map<String, Long> stats = new HashMap<>();

        for (Map<String, Object> row : results) {
            String type = (String) row.get("type");
            Long count = ((Number) row.get("count")).longValue();
            stats.put(type, count);
        }

        return stats;
    }

    /**
     * 按状态统计事件数量
     *
     * @return 状态统计
     */
    public Map<String, Long> countEventsByStatus() {
        List<Map<String, Object>> results = syncEventMapper.countByStatus();
        Map<String, Long> stats = new HashMap<>();

        for (Map<String, Object> row : results) {
            String status = (String) row.get("status");
            Long count = ((Number) row.get("count")).longValue();
            stats.put(status, count);
        }

        return stats;
    }

    /**
     * 获取平均同步延迟
     *
     * @return 平均延迟（秒）
     */
    public Double getAverageSyncDelay() {
        Double delay = syncEventMapper.getAverageSyncDelay();
        return delay != null ? delay : 0.0;
    }

    /**
     * 分析关联事件（例如：Push → Sync延迟）
     *
     * @param syncProjectId 同步项目ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 延迟分析结果
     */
    public Map<String, Object> analyzePushToSyncDelay(Long syncProjectId,
                                                       LocalDateTime startTime,
                                                       LocalDateTime endTime) {
        // 查询指定时间范围内的Push和Sync事件
        Page<SyncEvent> page = new Page<>(1, 1000);
        IPage<SyncEvent> events = syncEventMapper.selectPageWithFilters(
                page, syncProjectId, null, null, startTime, endTime);

        List<SyncEvent> pushEvents = new ArrayList<>();
        List<SyncEvent> syncEvents = new ArrayList<>();

        for (SyncEvent event : events.getRecords()) {
            if (SyncEvent.EventType.PUSH_DETECTED.equals(event.getEventType())) {
                pushEvents.add(event);
            } else if (SyncEvent.EventType.SYNC_FINISHED.equals(event.getEventType())) {
                syncEvents.add(event);
            }
        }

        // 计算延迟统计
        List<Long> delays = new ArrayList<>();
        for (SyncEvent pushEvent : pushEvents) {
            for (SyncEvent syncEvent : syncEvents) {
                if (pushEvent.getSyncProjectId().equals(syncEvent.getSyncProjectId()) &&
                    pushEvent.getCommitSha() != null &&
                    pushEvent.getCommitSha().equals(syncEvent.getCommitSha())) {

                    long delaySeconds = java.time.Duration.between(
                            pushEvent.getEventTime(),
                            syncEvent.getEventTime()).getSeconds();
                    delays.add(delaySeconds);
                    break;
                }
            }
        }

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("total_pairs", delays.size());
        analysis.put("push_count", pushEvents.size());
        analysis.put("sync_count", syncEvents.size());

        if (!delays.isEmpty()) {
            double avgDelay = delays.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long minDelay = delays.stream().mapToLong(Long::longValue).min().orElse(0L);
            long maxDelay = delays.stream().mapToLong(Long::longValue).max().orElse(0L);

            analysis.put("avg_delay_seconds", avgDelay);
            analysis.put("min_delay_seconds", minDelay);
            analysis.put("max_delay_seconds", maxDelay);
        } else {
            analysis.put("avg_delay_seconds", 0.0);
            analysis.put("min_delay_seconds", 0L);
            analysis.put("max_delay_seconds", 0L);
        }

        return analysis;
    }

    /**
     * 数据验证
     */
    private void validateEvent(SyncEvent event) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (event.getEventSource() == null || event.getEventSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Event source is required");
        }
        if (event.getStatus() == null || event.getStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("Event status is required");
        }
    }
}
