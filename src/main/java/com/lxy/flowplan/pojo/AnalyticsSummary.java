package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummary {

    @JsonProperty("today_actual_minutes")
    private Integer todayActualMinutes;

    @JsonProperty("today_completed_items")
    private Integer todayCompletedItems;

    @JsonProperty("completion_rate")
    private Integer completionRate;

    @JsonProperty("total_actual_minutes")
    private Integer totalActualMinutes;

    @JsonProperty("total_completed_items")
    private Integer totalCompletedItems;

    @JsonProperty("streak_days")
    private Integer streakDays;
}
