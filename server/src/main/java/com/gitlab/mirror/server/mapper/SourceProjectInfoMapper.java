package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * Source Project Info Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface SourceProjectInfoMapper extends BaseMapper<SourceProjectInfo> {
}
