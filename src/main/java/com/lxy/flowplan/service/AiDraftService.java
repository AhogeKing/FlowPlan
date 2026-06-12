package com.lxy.flowplan.service;

import com.lxy.flowplan.dto.ai.AiDraftRequest;
import com.lxy.flowplan.dto.ai.AiDraftResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiDraftService {
    AiDraftResponse startDraft(AiDraftRequest request);

    SseEmitter streamDraft(AiDraftRequest request);

    void clearSession(String sessionId);
}
