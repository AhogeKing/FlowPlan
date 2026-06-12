package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsTimeTrendPoint {

    private LocalDate date;

    @JsonProperty("recommended_minutes")
    private Integer recommendedMinutes;

    @JsonProperty("actual_minutes")
    private Integer actualMinutes;
}
