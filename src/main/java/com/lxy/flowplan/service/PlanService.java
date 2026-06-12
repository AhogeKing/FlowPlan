package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.PlanDetail;
import com.lxy.flowplan.pojo.PlanGenerateResult;

import java.time.LocalDate;
import java.util.List;

public interface PlanService {
    PlanGenerateResult generatePlan(Integer projectId);

    List<DailyPlan> listPlans(Integer projectId);

    PlanDetail getPlanByDate(Integer projectId, LocalDate planDate);

    String deleteAllPlanByProjectId(Integer projectId);
}
