package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.PlanSettingMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.pojo.PlanSetting;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.PlanSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PlanSettingServiceImpl implements PlanSettingService {
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_LOCAL = "LOCAL";

    private final PlanSettingMapper planSettingMapper;
    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    public PlanSettingServiceImpl(PlanSettingMapper planSettingMapper,
                                  ProjectMapper projectMapper,
                                  OperationLogService operationLogService) {
        this.planSettingMapper = planSettingMapper;
        this.projectMapper = projectMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public PlanSetting resolvePlanSetting(Project project) {
        validateProjectForSetting(project);
        // Project 专属设置优先级最高，用来覆盖用户全局排期偏好。
        PlanSetting localSetting = planSettingMapper.findLocalByProjectId(project.getId());

        if (localSetting != null) {
            validateResolvedSetting(localSetting, project);
            return localSetting;
        }

        PlanSetting globalSetting = findOrCreateGlobalSetting(project.getUserId());
        validateResolvedSetting(globalSetting, project);
        return globalSetting;
    }

    @Override
    public List<PlanSetting> selectListPlanSetting() {
        Integer userId = requireCurrentUserId();
        findOrCreateGlobalSetting(userId);
        return planSettingMapper.selectList(new LambdaQueryWrapper<PlanSetting>()
                .eq(PlanSetting::getUserId, userId)
                .orderByAsc(PlanSetting::getScope)
                .orderByAsc(PlanSetting::getProjectId));
    }

    @Override
    public PlanSetting selectGlobalPlanSetting() {
        return findOrCreateGlobalSetting(requireCurrentUserId());
    }

    @Override
    public PlanSetting selectLocalPlanSetting(Integer projectId) {
        // 先借 Project 做用户归属校验，避免直接按 projectId 读取越权设置。
        getCurrentUserProject(projectId);
        return planSettingMapper.selectOne(new LambdaQueryWrapper<PlanSetting>()
                .eq(PlanSetting::getUserId, AppUserContext.getUserId())
                .eq(PlanSetting::getProjectId, projectId)
                .eq(PlanSetting::getScope, SCOPE_LOCAL)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addLocalPlanSetting(Integer projectId, PlanSetting planSetting) {
        Project project = getCurrentUserProject(projectId);
        if (selectLocalPlanSetting(projectId) != null) {
            throw new IllegalArgumentException("当前 Project 已存在 LOCAL PlanSetting");
        }

        prepareLocalSettingForSave(project, planSetting, null);
        planSetting.setCreatedAt(LocalDateTime.now());
        planSetting.setUpdatedAt(LocalDateTime.now());

        planSettingMapper.insert(planSetting);
        markProjectNeedReplan(project);
        operationLogService.log("SETTING", "CREATE", "Create Local PlanSetting for Project " + projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLocalPlanSetting(Integer projectId, PlanSetting planSetting) {
        Project project = getCurrentUserProject(projectId);
        PlanSetting dbSetting = getCurrentUserLocalSetting(projectId);

        prepareLocalSettingForSave(project, planSetting, dbSetting.getId());
        planSetting.setCreatedAt(dbSetting.getCreatedAt());
        planSetting.setUpdatedAt(LocalDateTime.now());

        planSettingMapper.updateById(planSetting);
        markProjectNeedReplan(project);
        operationLogService.log("SETTING", "UPDATE", "Update Local PlanSetting for Project " + projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLocalPlanSetting(Integer projectId, PlanSetting planSetting) {
        Project project = getCurrentUserProject(projectId);
        PlanSetting dbSetting = getCurrentUserLocalSetting(projectId);

        if (planSetting != null && planSetting.getId() != null && !Objects.equals(planSetting.getId(), dbSetting.getId())) {
            throw new IllegalArgumentException("PlanSetting id 与当前 Project 的 LOCAL 设置不一致");
        }

        planSettingMapper.deleteById(dbSetting.getId());
        markProjectNeedReplan(project);
        operationLogService.log("SETTING", "DELETE", "Delete Local PlanSetting for Project " + projectId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGlobalPlanSetting(PlanSetting planSetting) {
        Integer userId = requireCurrentUserId();
        PlanSetting dbSetting = findOrCreateGlobalSetting(userId);

        prepareGlobalSettingForSave(userId, planSetting, dbSetting.getId());
        planSetting.setCreatedAt(dbSetting.getCreatedAt());
        planSetting.setUpdatedAt(LocalDateTime.now());

        planSettingMapper.updateById(planSetting);
        markAllCurrentUserProjectsNeedReplan();
        operationLogService.log("SETTING", "UPDATE", "Update Global PlanSetting");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetGlobalPlanSetting() {
        Integer userId = requireCurrentUserId();
        PlanSetting dbSetting = findOrCreateGlobalSetting(userId);
        PlanSetting defaultSetting = buildDefaultGlobalSetting(userId);

        defaultSetting.setId(dbSetting.getId());
        defaultSetting.setCreatedAt(dbSetting.getCreatedAt());
        defaultSetting.setUpdatedAt(LocalDateTime.now());

        planSettingMapper.updateById(defaultSetting);
        markAllCurrentUserProjectsNeedReplan();
        operationLogService.log("SETTING", "RESET", "Reset Global PlanSetting");
    }

    private PlanSetting findOrCreateGlobalSetting(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 id 不能为空");
        }
        // GLOBAL 是兜底配置：用户第一次访问设置或生成计划时自动补齐。
        PlanSetting globalSetting = planSettingMapper.findGlobalByUserId(userId);
        if (globalSetting != null) {
            validateGlobalSetting(globalSetting, userId);
            return globalSetting;
        }

        PlanSetting setting = buildDefaultGlobalSetting(userId);
        setting.setCreatedAt(LocalDateTime.now());
        setting.setUpdatedAt(LocalDateTime.now());
        planSettingMapper.insert(setting);
        return setting;
    }

    private PlanSetting buildDefaultGlobalSetting(Integer userId) {
        PlanSetting setting = new PlanSetting();
        setting.setUserId(userId);
        setting.setScope(SCOPE_GLOBAL);
        setting.setProjectId(null);
        setting.setBaseDailyMinutes(120);

        setting.setMonRatio(100);
        setting.setTueRatio(100);
        setting.setWedRatio(100);
        setting.setThuRatio(100);
        setting.setFriRatio(100);
        setting.setSatRatio(100);
        setting.setSunRatio(100);

        setting.setDailyMinMinutes(20);
        setting.setDailyMaxMinutes(120);

        setting.setTaskMinCountPerDay(1);
        setting.setTaskMaxCountPerDay(4);

        setting.setMinPlanItemMinutes(20);
        setting.setMaxPlanItemMinutes(120);
        setting.setTimeBlockMinutes(10);

        setting.setBalanceFactor(50);
        return setting;
    }

    private void prepareLocalSettingForSave(Project project, PlanSetting planSetting, Integer settingId) {
        if (planSetting == null) {
            throw new IllegalArgumentException("PlanSetting 不能为空");
        }
        if (planSetting.getProjectId() != null && !Objects.equals(planSetting.getProjectId(), project.getId())) {
            throw new IllegalArgumentException("PlanSetting projectId 与路径 Project 不一致");
        }

        // 归属字段由服务端从路径和当前用户推导，避免前端伪造 userId/projectId/scope。
        planSetting.setId(settingId);
        planSetting.setUserId(project.getUserId());
        planSetting.setScope(SCOPE_LOCAL);
        planSetting.setProjectId(project.getId());

        validateLocalSetting(planSetting, project);
    }

    private void prepareGlobalSettingForSave(Integer userId, PlanSetting planSetting, Integer settingId) {
        if (planSetting == null) {
            throw new IllegalArgumentException("PlanSetting 不能为空");
        }
        if (planSetting.getProjectId() != null) {
            throw new IllegalArgumentException("GLOBAL PlanSetting 不能绑定 projectId");
        }

        // 全局设置只能属于当前用户，且不能绑定任何 Project。
        planSetting.setId(settingId);
        planSetting.setUserId(userId);
        planSetting.setScope(SCOPE_GLOBAL);
        planSetting.setProjectId(null);

        validateGlobalSetting(planSetting, userId);
    }

    private void validateProjectForSetting(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("Project 不能为空");
        }
        if (project.getId() == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        if (project.getUserId() == null) {
            throw new IllegalArgumentException("Project userId 不能为空");
        }
    }

    private void validateResolvedSetting(PlanSetting setting, Project project) {
        validateProjectForSetting(project);
        if (setting == null) {
            throw new IllegalArgumentException("PlanSetting 不能为空");
        }
        if (SCOPE_GLOBAL.equals(setting.getScope())) {
            validateGlobalSetting(setting, project.getUserId());
            return;
        }
        validateLocalSetting(setting, project);
    }

    private void validateGlobalSetting(PlanSetting setting, Integer userId) {
        if (setting.getUserId() == null || !setting.getUserId().equals(userId)) {
            throw new IllegalArgumentException("PlanSetting 不属于当前用户");
        }
        if (!SCOPE_GLOBAL.equals(setting.getScope())) {
            throw new IllegalArgumentException("GLOBAL PlanSetting 的 scope 必须是 GLOBAL");
        }
        if (setting.getProjectId() != null) {
            throw new IllegalArgumentException("GLOBAL PlanSetting 不能绑定 projectId");
        }
        validateSettingValues(setting);
    }

    private void validateLocalSetting(PlanSetting setting, Project project) {
        if (setting.getUserId() == null || !setting.getUserId().equals(project.getUserId())) {
            throw new IllegalArgumentException("PlanSetting 不属于当前 Project 用户");
        }
        if (!SCOPE_LOCAL.equals(setting.getScope())) {
            throw new IllegalArgumentException("LOCAL PlanSetting 的 scope 必须是 LOCAL");
        }
        if (!Objects.equals(setting.getProjectId(), project.getId())) {
            throw new IllegalArgumentException("LOCAL PlanSetting 必须绑定当前 Project");
        }
        validateSettingValues(setting);
    }

    private void validateSettingValues(PlanSetting setting) {
        // 这里的校验和 SQL CHECK 约束保持一致，把错误尽量提前到 service 层返回。
        requirePositive(setting.getBaseDailyMinutes(), "baseDailyMinutes");
        requireNonNegative(setting.getMonRatio(), "monRatio");
        requireNonNegative(setting.getTueRatio(), "tueRatio");
        requireNonNegative(setting.getWedRatio(), "wedRatio");
        requireNonNegative(setting.getThuRatio(), "thuRatio");
        requireNonNegative(setting.getFriRatio(), "friRatio");
        requireNonNegative(setting.getSatRatio(), "satRatio");
        requireNonNegative(setting.getSunRatio(), "sunRatio");

        requireNonNegative(setting.getDailyMinMinutes(), "dailyMinMinutes");
        requireNonNull(setting.getDailyMaxMinutes(), "dailyMaxMinutes");
        if (setting.getDailyMaxMinutes() < setting.getDailyMinMinutes()) {
            throw new IllegalArgumentException("dailyMaxMinutes 不能小于 dailyMinMinutes");
        }

        requireNonNegative(setting.getTaskMinCountPerDay(), "taskMinCountPerDay");
        requirePositive(setting.getTaskMaxCountPerDay(), "taskMaxCountPerDay");
        if (setting.getTaskMaxCountPerDay() < setting.getTaskMinCountPerDay()) {
            throw new IllegalArgumentException("taskMaxCountPerDay 不能小于 taskMinCountPerDay");
        }

        requirePositive(setting.getMinPlanItemMinutes(), "minPlanItemMinutes");
        requireNonNull(setting.getMaxPlanItemMinutes(), "maxPlanItemMinutes");
        if (setting.getMaxPlanItemMinutes() < setting.getMinPlanItemMinutes()) {
            throw new IllegalArgumentException("maxPlanItemMinutes 不能小于 minPlanItemMinutes");
        }
        if (setting.getDailyMaxMinutes() < setting.getMinPlanItemMinutes()) {
            throw new IllegalArgumentException("dailyMaxMinutes 不能小于 minPlanItemMinutes");
        }

        requirePositive(setting.getTimeBlockMinutes(), "timeBlockMinutes");
        if (setting.getTimeBlockMinutes() > setting.getDailyMaxMinutes()) {
            throw new IllegalArgumentException("timeBlockMinutes 不能大于 dailyMaxMinutes");
        }
        requireNonNull(setting.getBalanceFactor(), "balanceFactor");
        if (setting.getBalanceFactor() < 0 || setting.getBalanceFactor() > 100) {
            throw new IllegalArgumentException("balanceFactor 必须在 0 到 100 之间");
        }
    }

    private PlanSetting getCurrentUserLocalSetting(Integer projectId) {
        PlanSetting setting = selectLocalPlanSetting(projectId);
        if (setting == null) {
            throw new IllegalArgumentException("当前 Project 不存在 LOCAL PlanSetting");
        }
        return setting;
    }

    private Project getCurrentUserProject(Integer projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        Project project = projectMapper.selectByIdAndUserId(projectId, requireCurrentUserId());
        if (project == null) {
            throw new IllegalArgumentException("Project 不存在或不属于当前用户");
        }
        return project;
    }

    private Integer requireCurrentUserId() {
        Integer userId = AppUserContext.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        return userId;
    }

    private void markProjectNeedReplan(Project project) {
        project.setNeedReplan(true);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    private void markAllCurrentUserProjectsNeedReplan() {
        // GLOBAL 改动会影响所有未使用 LOCAL 覆盖的项目；这里保守地标记当前用户全部项目。
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getUserId, requireCurrentUserId())
                .set(Project::getNeedReplan, true)
                .set(Project::getUpdatedAt, LocalDateTime.now()));
    }

    private void requireNonNull(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("PlanSetting " + fieldName + " 不能为空");
        }
    }

    private void requirePositive(Integer value, String fieldName) {
        requireNonNull(value, fieldName);
        if (value <= 0) {
            throw new IllegalArgumentException("PlanSetting " + fieldName + " 必须大于 0");
        }
    }

    private void requireNonNegative(Integer value, String fieldName) {
        requireNonNull(value, fieldName);
        if (value < 0) {
            throw new IllegalArgumentException("PlanSetting " + fieldName + " 不能小于 0");
        }
    }
}
