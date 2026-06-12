package com.lxy.flowplan.dto.ai;

import lombok.Data;

@Data
public class PlanSettingDraft {
    private Integer baseDailyMinutes;
    private Integer monRatio;
    private Integer tueRatio;
    private Integer wedRatio;
    private Integer thuRatio;
    private Integer friRatio;
    private Integer satRatio;
    private Integer sunRatio;
    private Integer dailyMinMinutes;
    private Integer dailyMaxMinutes;
    private Integer taskMinCountPerDay;
    private Integer taskMaxCountPerDay;
    private Integer minPlanItemMinutes;
    private Integer maxPlanItemMinutes;
    private Integer timeBlockMinutes;
    private Integer balanceFactor;
}
