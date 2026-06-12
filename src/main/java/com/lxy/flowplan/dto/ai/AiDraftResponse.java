package com.lxy.flowplan.dto.ai;

import lombok.Data;

@Data
public class AiDraftResponse {
    private String sessionId;
    private AiDraftStage stage;
    private String reply;
    private DomainType domainType;
    private Boolean fallbackUsed;
    private AiProjectDraft draft;
}
