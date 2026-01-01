package com.neu.easypam.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neu.easypam.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
