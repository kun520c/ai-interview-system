package com.kun.aiinterview.user.mapper;

import com.kun.aiinterview.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User getUserById(@Param("id") Long id);

    User getUserByAccount(@Param("account") String account);

    User getUserByEmail(@Param("email") String email);
}
