package com.lxy.flowplan.dto.ai;

import lombok.Data;

@Data
public class AiDraftApplyRequest {
    private String sessionId;
    private AiProjectDraft draft;
}
