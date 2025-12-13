package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Push Mirror Config Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface PushMirrorConfigMapper extends BaseMapper<PushMirrorConfig> {

    /**
     * Query by sync project ID
     */
    @Select("SELECT * FROM push_mirror_config WHERE sync_project_id = #{syncProjectId}")
    PushMirrorConfig selectBySyncProjectId(@Param("syncProjectId") Long syncProjectId);

    /**
     * Query by last update status
     */
    @Select("SELECT * FROM push_mirror_config WHERE last_update_status = #{status} ORDER BY last_update_at DESC")
    List<PushMirrorConfig> selectByUpdateStatus(@Param("status") String status);

    /**
     * Query mirrors with consecutive failures
     */
    @Select("SELECT * FROM push_mirror_config WHERE consecutive_failures >= #{threshold} ORDER BY consecutive_failures DESC")
    List<PushMirrorConfig> selectByFailureThreshold(@Param("threshold") Integer threshold);

    /**
     * Get mirror statistics
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN last_update_status = 'finished' THEN 1 ELSE 0 END) as success_count, " +
            "SUM(CASE WHEN last_update_status = 'failed' THEN 1 ELSE 0 END) as failed_count, " +
            "SUM(CASE WHEN last_update_status = 'started' THEN 1 ELSE 0 END) as running_count, " +
            "SUM(CASE WHEN consecutive_failures > 0 THEN 1 ELSE 0 END) as has_failures_count, " +
            "AVG(consecutive_failures) as avg_failures " +
            "FROM push_mirror_config")
    Map<String, Object> getMirrorStatistics();

    /**
     * Count by update status
     */
    @Select("SELECT last_update_status as status, COUNT(*) as count FROM push_mirror_config GROUP BY last_update_status")
    List<Map<String, Object>> countByUpdateStatus();
}
