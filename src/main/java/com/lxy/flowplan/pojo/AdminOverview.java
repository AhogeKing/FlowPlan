package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOverview {

    @JsonProperty("user_count")
    private Long userCount;

    @JsonProperty("project_count")
    private Long projectCount;

    @JsonProperty("task_count")
    private Long taskCount;

    @JsonProperty("today_plan_count")
    private Long todayPlanCount;

    @JsonProperty("today_checkin_count")
    private Long todayCheckinCount;

    @JsonProperty("ai_call_count")
    private Long aiCallCount;
}
