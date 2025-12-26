package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SyncResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
