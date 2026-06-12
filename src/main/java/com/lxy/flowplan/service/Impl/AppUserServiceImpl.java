package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lxy.flowplan.pojo.AppUser;
import com.lxy.flowplan.mapper.AppUserMapper;
import com.lxy.flowplan.service.AppUserService;
import com.lxy.flowplan.util.Md5Util;
import org.springframework.stereotype.Service;

@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;

    public AppUserServiceImpl(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public AppUser findByUserName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        username = username.trim();
        // username 有唯一约束，selectOne 能表达“最多一条”的业务预期。
        return appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getUsername, username)
        );
    }

    @Override
    public void register(String username, String password, String email) {
        username = normalizeRequired(username);
        validateUsername(username);
        validatePassword(password);
        email = normalizeEmail(username, email);

        if (existsUsername(username)) {
            throw new IllegalArgumentException("用户名已占用");
        }
        if (existsEmail(email)) {
            throw new IllegalArgumentException("邮箱已占用");
        }

        String encryptPassword = Md5Util.encrypt(password);
        AppUser appUser = new AppUser();
        appUser.setUsername(username);
        appUser.setPassword(encryptPassword);
        appUser.setEmail(email);
        appUserMapper.insert(appUser);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("用户名" + "不能为空");
        }
        return value.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() > 255) {
            throw new IllegalArgumentException("密码长度不能超过 255 个字符");
        }
    }

    private String normalizeEmail(String username, String email) {
        if (email == null || email.isBlank()) {
            return username + "@flowplan.local";
        }
        email = email.trim();
        if (email.length() > 100) {
            throw new IllegalArgumentException("邮箱长度不能超过 100 个字符");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return email;
    }

    private void validateUsername(String username) {
        if (username.length() > 50) {
            throw new IllegalArgumentException("用户名长度不能超过 50 个字符");
        }
    }

    private boolean existsUsername(String username) {
        return appUserMapper.exists(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getUsername, username)
        );
    }

    private boolean existsEmail(String email) {
        return appUserMapper.exists(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getEmail, email)
        );
    }
}
