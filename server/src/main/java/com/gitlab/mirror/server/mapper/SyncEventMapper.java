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
}
