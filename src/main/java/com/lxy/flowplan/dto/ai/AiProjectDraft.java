package com.lxy.flowplan.dto.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiProjectDraft {
    private ProjectDraft project;
    private List<TaskDraft> tasks = new ArrayList<>();
    private PlanSettingDraft setting;
    private String explanation;
    private List<String> warnings = new ArrayList<>();
    private DomainType domainType;
}
