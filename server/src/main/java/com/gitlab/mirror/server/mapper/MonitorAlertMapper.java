package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.MonitorAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * Monitor Alert Mapper
 * <p>
 * MyBatis-Plus mapper for monitor_alert table
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface MonitorAlertMapper extends BaseMapper<MonitorAlert> {
    // Inherits CRUD methods from BaseMapper
    // Additional custom queries can be added here if needed
}
