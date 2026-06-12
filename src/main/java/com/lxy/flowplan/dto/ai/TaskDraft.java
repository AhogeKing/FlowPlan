package com.lxy.flowplan.dto.ai;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskDraft {
    private String title;
    private String description;
    private Integer weight;
    private Integer minSessionMinutes;
    private LocalDate beginDate;
    private LocalDate deadline;
}
