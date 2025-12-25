package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * Sync Task Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncTaskMapper extends BaseMapper<SyncTask> {

    /**
     * Query tasks ready for scheduling
     *
     * @param taskType    Task type (push/pull)
     * @param taskStatus  Task status (waiting)
     * @param currentTime Current time
     * @param maxFailures Maximum consecutive failures
     * @param limit       Result limit
     * @return List of tasks ready for scheduling
     */
    @Select("SELECT * FROM sync_task " +
            "WHERE task_type = #{taskType} " +
            "AND task_status = #{taskStatus} " +
            "AND next_run_at <= #{currentTime} " +
            "AND consecutive_failures < #{maxFailures} " +
            "ORDER BY next_run_at ASC " +
            "LIMIT #{limit}")
    List<SyncTask> selectTasksForScheduling(
            @Param("taskType") String taskType,
            @Param("taskStatus") String taskStatus,
            @Param("currentTime") Instant currentTime,
            @Param("maxFailures") int maxFailures,
            @Param("limit") int limit
    );

    /**
     * Query Pull tasks with priority ordering
     *
     * @param currentTime Current time
     * @param maxFailures Maximum consecutive failures
     * @param limit       Result limit
     * @return List of Pull tasks ordered by priority
     */
    @Select("SELECT t.* FROM sync_task t " +
            "INNER JOIN pull_sync_config c ON t.sync_project_id = c.sync_project_id " +
            "INNER JOIN sync_project p ON t.sync_project_id = p.id " +
            "WHERE t.task_type = 'pull' " +
            "AND t.task_status = 'waiting' " +
            "AND t.next_run_at <= #{currentTime} " +
            "AND c.enabled = true " +
            "AND p.enabled = true " +
            "AND t.consecutive_failures < #{maxFailures} " +
            "ORDER BY " +
            "CASE c.priority " +
            "  WHEN 'critical' THEN 1 " +
            "  WHEN 'high' THEN 2 " +
            "  WHEN 'normal' THEN 3 " +
            "  WHEN 'low' THEN 4 " +
            "END, " +
            "t.next_run_at ASC " +
            "LIMIT #{limit}")
    List<SyncTask> selectPullTasksWithPriority(
            @Param("currentTime") Instant currentTime,
            @Param("maxFailures") int maxFailures,
            @Param("limit") int limit
    );
}
