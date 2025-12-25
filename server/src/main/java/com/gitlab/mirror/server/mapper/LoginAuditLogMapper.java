package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.LoginAuditLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LoginAuditLog Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface LoginAuditLogMapper extends BaseMapper<LoginAuditLog> {

    /**
     * Query login history by username
     *
     * @param username Username
     * @param limit    Maximum number of records
     * @return List of login audit logs
     */
    @Select("SELECT * FROM login_audit_log WHERE username = #{username} ORDER BY created_at DESC LIMIT #{limit}")
    List<LoginAuditLog> selectByUsername(@Param("username") String username, @Param("limit") int limit);

    /**
     * Query login history by IP address
     *
     * @param ip    IP address
     * @param limit Maximum number of records
     * @return List of login audit logs
     */
    @Select("SELECT * FROM login_audit_log WHERE ip_address = #{ip} ORDER BY created_at DESC LIMIT #{limit}")
    List<LoginAuditLog> selectByIpAddress(@Param("ip") String ip, @Param("limit") int limit);

    /**
     * Delete old audit log records
     *
     * @param before Delete records created before this timestamp
     * @return Number of deleted records
     */
    @Delete("DELETE FROM login_audit_log WHERE created_at < #{before}")
    int deleteOldRecords(@Param("before") LocalDateTime before);
}
