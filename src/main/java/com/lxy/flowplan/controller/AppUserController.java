package com.lxy.flowplan.controller;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.pojo.AppUser;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.AppUserService;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.TokenRevocationService;
import com.lxy.flowplan.util.JwtUtil;
import com.lxy.flowplan.util.Md5Util;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app-user")
public class AppUserController {

    private final AppUserService appUserService;
    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;
    private final OperationLogService operationLogService;

    @PostMapping("/register")
    public Result<Void> register(@RequestParam(required = false) String username,
                                 @RequestParam(required = false) String password,
                                 @RequestParam(required = false) String email) {
        appUserService.register(username, password, email);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<String> login(@RequestParam(required = false) String username,
                                @RequestParam(required = false) String password) {
        if (username == null || username.isBlank()) {
            return Result.error("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return Result.error("密码不能为空");
        }
        username = username.trim();

        // 根据用户名查询 AppUser
        AppUser loginUser = appUserService.findByUserName(username);

        // 判断是否查询到
        if (loginUser == null) {
            return Result.error("用户名错误");
        }

        // 判断密码是否正确
        if (Md5Util.getMD5String(password).equals(loginUser.getPassword())) {
            // 登录成功
            Map<String, Object> claims = new HashMap<>();
            claims.put("id", loginUser.getId());
            claims.put("email", loginUser.getEmail());
            claims.put("role", loginUser.getRole());
            String token = jwtUtil.createToken(loginUser.getUsername(), claims);
            operationLogService.logLoginSuccess(loginUser);
            return Result.success(token);
        }
        return Result.error("密码错误");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        tokenRevocationService.revoke(token);
        AppUserContext.remove();
        return Result.success(null);
    }

    @GetMapping("/info")
    public Result<AppUser> appUserInfo() {
        AppUser appUser = appUserService.findByUserName(AppUserContext.getUsername());
        return Result.success(appUser);
    }

    @GetMapping({"/register", "/login"})
    public Result<Void> methodNotSupported() {
        return Result.error("该接口需要使用 POST 请求，不能直接在浏览器地址栏用 GET 访问");
    }
}
