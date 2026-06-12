package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.PlanSetting;
import com.lxy.flowplan.pojo.Project;

import java.util.List;

public interface PlanSettingService {
    // Plan 生成时使用：优先取 Project 的 LOCAL 设置，没有则取/创建当前用户的 GLOBAL 设置。
    PlanSetting resolvePlanSetting(Project project);

    // 查看当前用户的所有设置；若全局设置不存在，会先补一条默认 GLOBAL。
    List<PlanSetting> selectListPlanSetting();

    // 查询当前用户的全局设置；不存在时自动创建默认配置。
    PlanSetting selectGlobalPlanSetting();

    // 查询 Project 专属设置；只允许访问当前用户自己的 Project。
    PlanSetting selectLocalPlanSetting(Integer projectId);

    // 新增 Project 专属设置；每个 Project 最多只能有一条 LOCAL。
    void addLocalPlanSetting(Integer projectId, PlanSetting planSetting);

    // 更新 Project 专属设置；路径 projectId 优先，请求体中的归属字段不可信。
    void updateLocalPlanSetting(Integer projectId, PlanSetting planSetting);

    // 删除 Project 专属设置，删除后计划生成会回退到 GLOBAL。
    void deleteLocalPlanSetting(Integer projectId, PlanSetting planSetting);

    // 更新当前用户的全局设置，会影响没有 LOCAL 设置的项目。
    void updateGlobalPlanSetting(PlanSetting planSetting);

    // 恢复当前用户的全局设置为系统默认值。
    void resetGlobalPlanSetting();
}
