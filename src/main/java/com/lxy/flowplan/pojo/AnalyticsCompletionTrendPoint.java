package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsCompletionTrendPoint {

    private LocalDate date;

    @JsonProperty("recommended_count")
    private Integer recommendedCount;

    @JsonProperty("completed_count")
    private Integer completedCount;
}
