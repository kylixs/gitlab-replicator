package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Pull Sync Config Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface PullSyncConfigMapper extends BaseMapper<PullSyncConfig> {
}
