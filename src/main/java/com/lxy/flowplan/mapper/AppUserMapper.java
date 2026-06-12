package com.lxy.flowplan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lxy.flowplan.pojo.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
    // 目前 AppUser 只需要 MyBatis-Plus 提供的基础 CRUD。
}
