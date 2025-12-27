package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.entity.SyncEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sync Event Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncEventMapper extends BaseMapper<SyncEvent> {

    /**
     * Query events by sync project ID
     */
    @Select("SELECT * FROM sync_event WHERE sync_project_id = #{syncProjectId} ORDER BY event_time DESC LIMIT #{limit}")
    List<SyncEvent> selectBySyncProjectId(@Param("syncProjectId") Long syncProjectId, @Param("limit") Integer limit);

    /**
     * Query events by type
     */
    @Select("SELECT * FROM sync_event WHERE event_type = #{eventType} ORDER BY event_time DESC")
    List<SyncEvent> selectByEventType(@Param("eventType") String eventType);

    /**
     * Query events by status
     */
    @Select("SELECT * FROM sync_event WHERE status = #{status} ORDER BY event_time DESC")
    List<SyncEvent> selectByStatus(@Param("status") String status);

    /**
     * Query events within time range
     */
    @Select("SELECT * FROM sync_event WHERE event_time BETWEEN #{startTime} AND #{endTime} ORDER BY event_time DESC")
    List<SyncEvent> selectByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * Query events with pagination and filters
     */
    @Select("<script>" +
            "SELECT * FROM sync_event WHERE 1=1" +
            "<if test='syncProjectId != null'> AND sync_project_id = #{syncProjectId}</if>" +
            "<if test='eventType != null'> AND event_type = #{eventType}</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "<if test='startTime != null'> AND event_time &gt;= #{startTime}</if>" +
            "<if test='endTime != null'> AND event_time &lt;= #{endTime}</if>" +
            " ORDER BY event_time DESC" +
            "</script>")
    IPage<SyncEvent> selectPageWithFilters(Page<?> page,
                                            @Param("syncProjectId") Long syncProjectId,
                                            @Param("eventType") String eventType,
                                            @Param("status") String status,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * Count events by type
     */
    @Select("SELECT event_type as type, COUNT(*) as count FROM sync_event GROUP BY event_type")
    List<Map<String, Object>> countByEventType();

    /**
     * Count events by status
     */
    @Select("SELECT status, COUNT(*) as count FROM sync_event GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /**
     * Get event statistics
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as success_count, " +
            "SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed_count, " +
            "AVG(CASE WHEN duration_seconds IS NOT NULL THEN duration_seconds ELSE 0 END) as avg_duration " +
            "FROM sync_event")
    Map<String, Object> getEventStatistics();

    /**
     * Get average sync delay (time from push to sync finish)
     */
    @Select("SELECT AVG(duration_seconds) as avg_delay FROM sync_event WHERE event_type = 'sync_finished' AND duration_seconds IS NOT NULL")
    Double getAverageSyncDelay();

    /**
     * Get hourly trend statistics for last 24 hours
     * Returns hour, total count, success count, and failed count for each hour window
     */
    @Select("SELECT " +
            "HOUR(hour_start) as hour, " +
            "COALESCE(COUNT(e.id), 0) as total, " +
            "COALESCE(SUM(CASE WHEN e.status = 'success' THEN 1 ELSE 0 END), 0) as success, " +
            "COALESCE(SUM(CASE WHEN e.status = 'failed' THEN 1 ELSE 0 END), 0) as failed " +
            "FROM (" +
            "  SELECT DATE_ADD(DATE_FORMAT(#{currentHour}, '%Y-%m-%d %H:00:00'), INTERVAL -n HOUR) as hour_start " +
            "  FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 " +
            "        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 " +
            "        UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 " +
            "        UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23) hours " +
            ") h " +
            "LEFT JOIN sync_event e ON e.event_time >= h.hour_start AND e.event_time < DATE_ADD(h.hour_start, INTERVAL 1 HOUR) " +
            "GROUP BY hour_start " +
            "ORDER BY hour_start ASC")
    List<Map<String, Object>> getHourlyTrend24h(@Param("currentHour") LocalDateTime currentHour);

    /**
     * Get hourly event type trend for last 24 hours
     * Returns hour, event_type, and count for each combination
     */
    @Select("SELECT " +
            "HOUR(hour_start) as hour, " +
            "COALESCE(e.event_type, '') as event_type, " +
            "COALESCE(COUNT(e.id), 0) as count " +
            "FROM (" +
            "  SELECT DATE_ADD(DATE_FORMAT(#{currentHour}, '%Y-%m-%d %H:00:00'), INTERVAL -n HOUR) as hour_start " +
            "  FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 " +
            "        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 " +
            "        UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 " +
            "        UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23) hours " +
            ") h " +
            "LEFT JOIN sync_event e ON e.event_time >= h.hour_start AND e.event_time < DATE_ADD(h.hour_start, INTERVAL 1 HOUR) " +
            "WHERE e.event_type IS NULL OR e.event_type IN (" +
            "  SELECT DISTINCT event_type FROM sync_event WHERE event_time >= DATE_ADD(#{currentHour}, INTERVAL -24 HOUR)" +
            ") " +
            "GROUP BY hour_start, e.event_type " +
            "ORDER BY hour_start ASC, e.event_type ASC")
    List<Map<String, Object>> getHourlyEventTypeTrend24h(@Param("currentHour") LocalDateTime currentHour);

    /**
     * Get daily trend statistics for last 7 days
     * Returns date, total count, success count, and failed count for each day
     */
    @Select("SELECT " +
            "DATE(day_start) as date, " +
            "COALESCE(COUNT(e.id), 0) as total, " +
            "COALESCE(SUM(CASE WHEN e.status = 'success' THEN 1 ELSE 0 END), 0) as success, " +
            "COALESCE(SUM(CASE WHEN e.status = 'failed' THEN 1 ELSE 0 END), 0) as failed " +
            "FROM (" +
            "  SELECT DATE_ADD(CURDATE(), INTERVAL -n DAY) as day_start " +
            "  FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 " +
            "        UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) days " +
            ") d " +
            "LEFT JOIN sync_event e ON DATE(e.event_time) = d.day_start " +
            "GROUP BY day_start " +
            "ORDER BY day_start ASC")
    List<Map<String, Object>> getDailyTrend7d();

    /**
     * Get daily event type trend for last 7 days
     * Returns date, event_type, and count for each combination
     */
    @Select("SELECT " +
            "DATE(d.day_start) as date, " +
            "et.event_type, " +
            "COALESCE(COUNT(e.id), 0) as count " +
            "FROM (" +
            "  SELECT DATE_ADD(CURDATE(), INTERVAL -n DAY) as day_start " +
            "  FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 " +
            "        UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) days " +
            ") d " +
            "CROSS JOIN (" +
            "  SELECT DISTINCT event_type FROM sync_event " +
            "  WHERE event_time >= DATE_ADD(CURDATE(), INTERVAL -7 DAY)" +
            ") et " +
            "LEFT JOIN sync_event e ON DATE(e.event_time) = d.day_start AND e.event_type = et.event_type " +
            "GROUP BY d.day_start, et.event_type " +
            "ORDER BY d.day_start ASC, et.event_type ASC")
    List<Map<String, Object>> getDailyEventTypeTrend7d();
}
