package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gitlab.mirror.server.entity.SyncProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Sync Project Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncProjectMapper extends BaseMapper<SyncProject> {

    /**
     * Query project by project key (unique)
     */
    @Select("SELECT * FROM sync_project WHERE project_key = #{projectKey}")
    SyncProject selectByProjectKey(@Param("projectKey") String projectKey);

    /**
     * Query projects by sync status
     */
    @Select("SELECT * FROM sync_project WHERE sync_status = #{status} ORDER BY created_at DESC")
    List<SyncProject> selectByStatus(@Param("status") String status);

    /**
     * Query projects by sync method
     */
    @Select("SELECT * FROM sync_project WHERE sync_method = #{method} ORDER BY created_at DESC")
    List<SyncProject> selectByMethod(@Param("method") String method);

    /**
     * Query enabled/disabled projects
     */
    @Select("SELECT * FROM sync_project WHERE enabled = #{enabled} ORDER BY created_at DESC")
    List<SyncProject> selectByEnabled(@Param("enabled") Boolean enabled);

    /**
     * Query projects with pagination and filters
     */
    @Select("<script>" +
            "SELECT * FROM sync_project WHERE 1=1" +
            "<if test='status != null'> AND sync_status = #{status}</if>" +
            "<if test='method != null'> AND sync_method = #{method}</if>" +
            "<if test='enabled != null'> AND enabled = #{enabled}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    IPage<SyncProject> selectPageWithFilters(Page<?> page,
                                              @Param("status") String status,
                                              @Param("method") String method,
                                              @Param("enabled") Boolean enabled);

    /**
     * Count projects by status
     */
    @Select("SELECT sync_status as status, COUNT(*) as count FROM sync_project GROUP BY sync_status")
    List<Map<String, Object>> countByStatus();

    /**
     * Count projects by method
     */
    @Select("SELECT sync_method as method, COUNT(*) as count FROM sync_project GROUP BY sync_method")
    List<Map<String, Object>> countByMethod();

    /**
     * Get overall statistics
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) as enabled_count, " +
            "SUM(CASE WHEN sync_status = 'active' THEN 1 ELSE 0 END) as active_count, " +
            "SUM(CASE WHEN sync_status = 'failed' THEN 1 ELSE 0 END) as failed_count " +
            "FROM sync_project")
    Map<String, Object> getStatistics();
}
