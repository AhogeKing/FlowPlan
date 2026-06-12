package com.lxy.flowplan.interceptor;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.exception.JwtAuthenticationException;
import com.lxy.flowplan.service.TokenRevocationService;
import com.lxy.flowplan.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            throw new JwtAuthenticationException("缺少 Authorization 请求头");
        }
        if (tokenRevocationService.isRevoked(token)) {
            throw new JwtAuthenticationException("登录状态已失效，请重新登录");
        }
        Claims claims = jwtUtil.parseToken(token);
        AppUserContext.set(claims);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AppUserContext.remove();
    }
}
