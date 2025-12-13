package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Push Mirror Config Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface PushMirrorConfigMapper extends BaseMapper<PushMirrorConfig> {
}
