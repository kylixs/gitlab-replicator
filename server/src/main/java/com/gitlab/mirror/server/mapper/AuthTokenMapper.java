package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.AuthToken;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

/**
 * AuthToken Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthToken> {

    /**
     * Query token by token value
     *
     * @param token Token value
     * @return AuthToken entity or null if not found
     */
    @Select("SELECT * FROM auth_tokens WHERE token = #{token}")
    AuthToken selectByToken(@Param("token") String token);

    /**
     * Delete expired tokens
     *
     * @param now Current timestamp
     * @return Number of deleted records
     */
    @Delete("DELETE FROM auth_tokens WHERE expires_at < #{now}")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Update last used timestamp
     *
     * @param token Token value
     * @param now   Current timestamp
     * @return Number of updated records
     */
    @Update("UPDATE auth_tokens SET last_used_at = #{now} WHERE token = #{token}")
    int updateLastUsedAt(@Param("token") String token, @Param("now") LocalDateTime now);
}
