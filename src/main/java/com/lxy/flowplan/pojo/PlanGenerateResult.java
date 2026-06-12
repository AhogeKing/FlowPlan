package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class PlanGenerateResult {
    @JsonProperty("project_id")
    private Integer projectId;

    @JsonProperty("start_date")
    private LocalDate startDate;

    @JsonProperty("end_date")
    private LocalDate endDate;

    @JsonProperty("plan_count")
    private Integer planCount;

    @JsonProperty("item_count")
    private Integer itemCount;

    @JsonProperty("unscheduled_minutes")
    private Integer unscheduledMinutes;

    @JsonProperty("risk_level")
    private String riskLevel;
}
