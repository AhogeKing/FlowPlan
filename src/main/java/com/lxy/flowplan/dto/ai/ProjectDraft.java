package com.lxy.flowplan.dto.ai;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectDraft {
    private String name;
    private String description;
    private LocalDate beginDate;
    private LocalDate deadline;
}
