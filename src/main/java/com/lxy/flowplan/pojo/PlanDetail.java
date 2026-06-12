package com.lxy.flowplan.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PlanDetail {
    private DailyPlan plan;
    private List<DailyPlanItem> items;
}
