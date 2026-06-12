package com.lxy.flowplan.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lxy.flowplan.pojo.PlanGenerateResult;
import lombok.Data;

@Data
public class AiApplyResult {
    @JsonProperty("project_id")
    private Integer projectId;

    @JsonProperty("plan_result")
    private PlanGenerateResult planResult;

    private String message;
}
