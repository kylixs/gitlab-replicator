package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * Target Project Info Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface TargetProjectInfoMapper extends BaseMapper<TargetProjectInfo> {
}
