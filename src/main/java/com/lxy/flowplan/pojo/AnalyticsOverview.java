package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverview {

    private String range;

    private AnalyticsSummary summary;

    @JsonProperty("completion_trend")
    private List<AnalyticsCompletionTrendPoint> completionTrend;

    @JsonProperty("time_trend")
    private List<AnalyticsTimeTrendPoint> timeTrend;
}
