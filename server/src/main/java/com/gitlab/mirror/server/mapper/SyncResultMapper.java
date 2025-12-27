package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SyncResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Sync Result Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncResultMapper extends BaseMapper<SyncResult> {

    /**
     * Query sync result by sync project ID (most recent)
     */
    @Select("SELECT * FROM sync_result WHERE sync_project_id = #{syncProjectId} ORDER BY started_at DESC LIMIT 1")
    SyncResult selectBySyncProjectId(@Param("syncProjectId") Long syncProjectId);

    /**
     * Query sync results by sync project IDs in batch (most recent for each project)
     * Uses a correlated subquery to get the most recent result for each project
     */
    @Select("""
            <script>
            SELECT sr.* FROM sync_result sr
            WHERE sr.sync_project_id IN
            <foreach collection='syncProjectIds' item='id' open='(' separator=',' close=')'>
                #{id}
            </foreach>
            AND sr.id IN (
                SELECT MAX(id) FROM sync_result
                WHERE sync_project_id IN
                <foreach collection='syncProjectIds' item='id' open='(' separator=',' close=')'>
                    #{id}
                </foreach>
                GROUP BY sync_project_id
            )
            </script>
            """)
    List<SyncResult> selectBySyncProjectIds(@Param("syncProjectIds") List<Long> syncProjectIds);
}
