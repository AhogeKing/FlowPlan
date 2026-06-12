package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.AppUserMapper;
import com.lxy.flowplan.mapper.DailyPlanMapper;
import com.lxy.flowplan.mapper.OperationLogMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.mapper.TaskMapper;
import com.lxy.flowplan.pojo.AdminOperationLogPage;
import com.lxy.flowplan.pojo.AdminOverview;
import com.lxy.flowplan.pojo.AdminUserRow;
import com.lxy.flowplan.pojo.AppUser;
import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.OperationLog;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.repository.AiInteractionLogRepository;
import com.lxy.flowplan.repository.CheckinRecordRepository;
import com.lxy.flowplan.service.AdminService;
import com.lxy.flowplan.service.OperationLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AppUserMapper appUserMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final DailyPlanMapper dailyPlanMapper;
    private final OperationLogMapper operationLogMapper;
    private final CheckinRecordRepository checkinRecordRepository;
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final OperationLogService operationLogService;

    public AdminServiceImpl(AppUserMapper appUserMapper,
                            ProjectMapper projectMapper,
                            TaskMapper taskMapper,
                            DailyPlanMapper dailyPlanMapper,
                            OperationLogMapper operationLogMapper,
                            CheckinRecordRepository checkinRecordRepository,
                            AiInteractionLogRepository aiInteractionLogRepository,
                            OperationLogService operationLogService) {
        this.appUserMapper = appUserMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.operationLogMapper = operationLogMapper;
        this.checkinRecordRepository = checkinRecordRepository;
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.operationLogService = operationLogService;
    }

    @Override
    public AdminOverview getOverview() {
        LocalDate today = LocalDate.now();
        return new AdminOverview(
                appUserMapper.selectCount(null),
                projectMapper.selectCount(null),
                taskMapper.selectCount(null),
                dailyPlanMapper.selectCount(new LambdaQueryWrapper<DailyPlan>()
                        .eq(DailyPlan::getPlanDate, today)),
                safeTodayCheckinCount(today),
                safeAiCallCount()
        );
    }

    @Override
    public List<AdminUserRow> listUsers() {
        List<AppUser> users = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .orderByDesc(AppUser::getRegTime)
                .orderByDesc(AppUser::getId));
        Map<Integer, Long> projectCountMap = projectMapper.selectList(new LambdaQueryWrapper<Project>())
                .stream()
                .collect(Collectors.groupingBy(Project::getUserId, Collectors.counting()));
        Map<Integer, LocalDateTime> lastLoginMap = loadLastLoginMap();

        return users.stream()
                .map(user -> new AdminUserRow(
                        user.getId(),
                        user.getUsername(),
                        user.getRole(),
                        projectCountMap.getOrDefault(user.getId(), 0L),
                        user.getRegTime(),
                        lastLoginMap.get(user.getId())
                ))
                .toList();
    }

    @Override
    public AdminOperationLogPage listOperationLogs(Integer page, Integer size, String module) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        try {
            LambdaQueryWrapper<OperationLog> countWrapper = buildLogWrapper(module);
            Long total = operationLogMapper.selectCount(countWrapper);
            int totalPages = total == 0 ? 0 : (int) Math.ceil(total * 1.0 / safeSize);
            int offset = (safePage - 1) * safeSize;

            LambdaQueryWrapper<OperationLog> listWrapper = buildLogWrapper(module)
                    .orderByDesc(OperationLog::getCreateTime)
                    .orderByDesc(OperationLog::getId)
                    .last("LIMIT " + safeSize + " OFFSET " + offset);

            return new AdminOperationLogPage(
                    safePage,
                    safeSize,
                    total,
                    totalPages,
                    operationLogMapper.selectList(listWrapper)
            );
        } catch (Exception ignored) {
            return new AdminOperationLogPage(safePage, safeSize, 0L, 0, List.of());
        }
    }

    @Override
    public void updateUserRole(Integer userId, String role) {
        AppUser user = loadUser(userId);
        String normalizedRole = normalizeRole(role);
        Integer currentUserId = AppUserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(user.getId()) && !"ADMIN".equals(normalizedRole)) {
            throw new IllegalArgumentException("不能将当前登录管理员降级为普通用户");
        }
        if (normalizedRole.equalsIgnoreCase(user.getRole())) {
            return;
        }

        String oldRole = user.getRole();
        user.setRole(normalizedRole);
        appUserMapper.updateById(user);
        operationLogService.log(
                "ADMIN",
                "UPDATE",
                "Update User Role: " + user.getUsername() + " " + oldRole + " -> " + normalizedRole
        );
    }

    @Override
    public void deleteUser(Integer userId) {
        AppUser user = loadUser(userId);
        Integer currentUserId = AppUserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            throw new IllegalArgumentException("不能删除当前登录管理员");
        }

        appUserMapper.deleteById(user.getId());
        operationLogService.log(
                "ADMIN",
                "DELETE",
                "Delete User: " + user.getUsername() + " (" + user.getId() + ")"
        );
    }

    private LambdaQueryWrapper<OperationLog> buildLogWrapper(String module) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) {
            wrapper.eq(OperationLog::getModule, module.trim().toUpperCase());
        }
        return wrapper;
    }

    private AppUser loadUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (!"USER".equals(normalizedRole) && !"ADMIN".equals(normalizedRole)) {
            throw new IllegalArgumentException("角色只能是 USER 或 ADMIN");
        }
        return normalizedRole;
    }

    private Map<Integer, LocalDateTime> loadLastLoginMap() {
        try {
            return operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                            .eq(OperationLog::getModule, "USER")
                            .eq(OperationLog::getOperationType, "LOGIN")
                            .eq(OperationLog::getStatus, "SUCCESS")
                            .orderByDesc(OperationLog::getCreateTime))
                    .stream()
                    .collect(Collectors.toMap(
                            OperationLog::getUserId,
                            OperationLog::getCreateTime,
                            (first, ignored) -> first
                    ));
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private long safeTodayCheckinCount(LocalDate today) {
        try {
            return checkinRecordRepository.countByCheckinDate(today);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private long safeAiCallCount() {
        try {
            return aiInteractionLogRepository.count();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
