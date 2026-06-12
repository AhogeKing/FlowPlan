package com.lxy.flowplan.service;

import com.lxy.flowplan.dto.ai.AiApplyResult;
import com.lxy.flowplan.dto.ai.AiDraftApplyRequest;

public interface AiDraftApplyService {
    AiApplyResult applyDraft(AiDraftApplyRequest request);
}
