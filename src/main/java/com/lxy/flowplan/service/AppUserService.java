package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.AppUser;

public interface AppUserService {

    // 登录、注册占用检查和当前用户信息查询都复用这个入口。
    AppUser findByUserName(String username);

    // 注册时由 service 统一处理密码加密和默认邮箱。
    void register(String username, String password, String email);

}
