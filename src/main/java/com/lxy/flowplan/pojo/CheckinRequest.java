package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CheckinRequest {

    @JsonProperty("completed_minutes")
    private Integer completedMinutes;

    @JsonProperty("checkin_date")
    private LocalDate checkinDate;

    private String note;
}
