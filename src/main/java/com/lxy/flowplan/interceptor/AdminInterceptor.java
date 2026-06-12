package com.lxy.flowplan.interceptor;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.exception.AdminAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String role = AppUserContext.getRole();
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new AdminAccessDeniedException("仅管理员可以访问该接口");
        }
        return true;
    }
}
