package com.lxy.flowplan.service.Impl;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.OperationLogMapper;
import com.lxy.flowplan.pojo.AppUser;
import com.lxy.flowplan.pojo.OperationLog;
import com.lxy.flowplan.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final OperationLogMapper operationLogMapper;
    private volatile boolean operationLogAvailable = true;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void log(String module, String operationType, String description) {
        Integer userId = AppUserContext.getUserId();
        String username = AppUserContext.getUsername();
        if (userId == null || username == null) {
            return;
        }
        saveQuietly(buildLog(userId, username, module, operationType, description, STATUS_SUCCESS, null));
    }

    @Override
    public void logFailureForCurrentRequest(String errorMessage) {
        Integer userId = AppUserContext.getUserId();
        String username = AppUserContext.getUsername();
        if (userId == null || username == null) {
            return;
        }
        OperationDescriptor descriptor = inferCurrentOperation();
        if (descriptor == null) {
            return;
        }
        saveQuietly(buildLog(
                userId,
                username,
                descriptor.module(),
                descriptor.operationType(),
                descriptor.description(),
                STATUS_FAILED,
                errorMessage
        ));
    }

    @Override
    public void logLoginSuccess(AppUser user) {
        if (user == null || user.getId() == null) {
            return;
        }
        saveQuietly(buildLog(
                user.getId(),
                user.getUsername(),
                "USER",
                "LOGIN",
                "User Login: " + user.getUsername(),
                STATUS_SUCCESS,
                null
        ));
    }

    private OperationLog buildLog(Integer userId,
                                  String username,
                                  String module,
                                  String operationType,
                                  String description,
                                  String status,
                                  String errorMessage) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setModule(normalize(module));
        log.setOperationType(normalize(operationType));
        log.setDescription(description);
        log.setRequestMethod(currentRequest() == null ? null : currentRequest().getMethod());
        log.setRequestUrl(currentRequest() == null ? null : currentRequest().getRequestURI());
        log.setIp(resolveIp(currentRequest()));
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setCreateTime(LocalDateTime.now());
        return log;
    }

    private void saveQuietly(OperationLog operationLog) {
        if (!operationLogAvailable) {
            return;
        }
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            if (isSchemaMismatch(e)) {
                operationLogAvailable = false;
                log.warn("OperationLog table schema is not V1.3 yet. Please run migration/004_admin_operation_log_v1_3.sql. OperationLog writes will be skipped until restart.");
                return;
            }
            log.warn("OperationLog save failed. module={}, operationType={}, reason={}",
                    operationLog.getModule(), operationLog.getOperationType(), e.getMessage());
        }
    }

    private boolean isSchemaMismatch(Exception e) {
        Throwable cursor = e;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.contains("Unknown column")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private OperationDescriptor inferCurrentOperation() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        if ("GET".equals(method) || "OPTIONS".equals(method)) {
            return null;
        }

        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        if (path.contains("/task/")) {
            return new OperationDescriptor("TASK", operationType(method, path), "Task write request failed");
        }
        if (path.contains("/checkin")) {
            return new OperationDescriptor("CHECKIN", operationType(method, path), "Check-in request failed");
        }
        if (path.contains("/plan/generate")) {
            return new OperationDescriptor("PLAN", "GENERATE", "Generate Plan request failed");
        }
        if (path.contains("/plan/delete")) {
            return new OperationDescriptor("PLAN", "DELETE", "Delete Plan request failed");
        }
        if (path.contains("/project/")) {
            return new OperationDescriptor("PROJECT", operationType(method, path), "Project write request failed");
        }
        if (path.contains("/setting/")) {
            return new OperationDescriptor("SETTING", operationType(method, path), "PlanSetting request failed");
        }
        if (path.contains("/ai/draft/apply")) {
            return new OperationDescriptor("AI", "APPLY", "AI Draft Apply request failed");
        }
        if (path.contains("/ai/draft/start") || path.contains("/ai/draft/stream")) {
            return new OperationDescriptor("AI", "GENERATE", "AI Draft Generate request failed");
        }
        if (path.contains("/admin/users")) {
            return new OperationDescriptor("ADMIN", operationType(method, path), "Admin user management request failed");
        }
        return null;
    }

    private String operationType(String method, String path) {
        if ("POST".equals(method) && path.contains("/generate")) {
            return "GENERATE";
        }
        if ("POST".equals(method) && path.contains("/checkin")) {
            return "CHECKIN";
        }
        if ("POST".equals(method)) {
            return "CREATE";
        }
        if ("PUT".equals(method) || "PATCH".equals(method)) {
            return "UPDATE";
        }
        if ("DELETE".equals(method)) {
            return "DELETE";
        }
        return method;
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record OperationDescriptor(String module, String operationType, String description) {
    }
}
