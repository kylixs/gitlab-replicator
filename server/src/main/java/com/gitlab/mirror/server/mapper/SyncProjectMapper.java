package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SyncProject;
import org.apache.ibatis.annotations.Mapper;

/**
 * Sync Project Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SyncProjectMapper extends BaseMapper<SyncProject> {
}
