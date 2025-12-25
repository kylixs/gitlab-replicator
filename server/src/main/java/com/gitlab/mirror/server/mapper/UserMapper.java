package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * User Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * Query user by username
     *
     * @param username Username
     * @return User entity or null if not found
     */
    @Select("SELECT * FROM users WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);
}
