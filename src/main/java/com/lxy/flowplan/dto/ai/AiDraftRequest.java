package com.lxy.flowplan.dto.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiDraftRequest {
    private String sessionId;
    private String message;
    private List<AiChatMessage> messages = new ArrayList<>();
    private AiProjectDraft currentDraft;
}
