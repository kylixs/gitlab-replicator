package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SyncEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * Sync Event Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncEventMapper extends BaseMapper<SyncEvent> {
}
