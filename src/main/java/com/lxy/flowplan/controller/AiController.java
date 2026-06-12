package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.dto.ai.AiApplyResult;
import com.lxy.flowplan.dto.ai.AiDraftApplyRequest;
import com.lxy.flowplan.dto.ai.AiDraftRequest;
import com.lxy.flowplan.dto.ai.AiDraftResponse;
import com.lxy.flowplan.service.AiDraftApplyService;
import com.lxy.flowplan.service.AiDraftService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai/draft")
public class AiController {
    private final AiDraftService aiDraftService;
    private final AiDraftApplyService aiDraftApplyService;

    public AiController(AiDraftService aiDraftService, AiDraftApplyService aiDraftApplyService) {
        this.aiDraftService = aiDraftService;
        this.aiDraftApplyService = aiDraftApplyService;
    }

    @PostMapping("/start")
    public Result<AiDraftResponse> startDraft(@RequestBody AiDraftRequest request) {
        return Result.success(aiDraftService.startDraft(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDraft(@RequestBody AiDraftRequest request) {
        return aiDraftService.streamDraft(request);
    }

    @PostMapping("/apply")
    public Result<AiApplyResult> applyDraft(@RequestBody AiDraftApplyRequest request) {
        return Result.success(aiDraftApplyService.applyDraft(request));
    }

    @DeleteMapping("/session/{sessionId}")
    public Result<Void> clearSession(@PathVariable String sessionId) {
        aiDraftService.clearSession(sessionId);
        return Result.success();
    }
}
