package com.lxy.flowplan.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.dto.ai.AiChatMessage;
import com.lxy.flowplan.dto.ai.AiDraftRequest;
import com.lxy.flowplan.dto.ai.AiDraftResponse;
import com.lxy.flowplan.dto.ai.AiDraftStage;
import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.dto.ai.DomainType;
import io.jsonwebtoken.Claims;
import com.lxy.flowplan.service.AiDraftService;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.ai.AiAuditService;
import com.lxy.flowplan.service.ai.AiClient;
import com.lxy.flowplan.service.ai.AiDraftSanitizer;
import com.lxy.flowplan.service.ai.AiDraftSessionStore;
import com.lxy.flowplan.service.ai.AiPromptBuilder;
import com.lxy.flowplan.service.ai.AiTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Service
@Slf4j
public class AiDraftServiceImpl implements AiDraftService {
    private final AiTemplateService aiTemplateService;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiClient aiClient;
    private final AiDraftSanitizer aiDraftSanitizer;
    private final AiAuditService aiAuditService;
    private final AiDraftSessionStore aiDraftSessionStore;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;

    public AiDraftServiceImpl(AiTemplateService aiTemplateService,
                              AiPromptBuilder aiPromptBuilder,
                              AiClient aiClient,
                              AiDraftSanitizer aiDraftSanitizer,
                              AiAuditService aiAuditService,
                              AiDraftSessionStore aiDraftSessionStore,
                              ObjectMapper objectMapper,
                              OperationLogService operationLogService) {
        this.aiTemplateService = aiTemplateService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiClient = aiClient;
        this.aiDraftSanitizer = aiDraftSanitizer;
        this.aiAuditService = aiAuditService;
        this.aiDraftSessionStore = aiDraftSessionStore;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public AiDraftResponse startDraft(AiDraftRequest request) {
        String message = normalizeMessage(request);
        String sessionId = resolveSessionId(request);
        DraftContext context = buildDraftContext(request, message, sessionId);
        AiDraftResponse response = generateDraftResponse(context);
        saveContext(context, response);
        operationLogService.log("AI", "GENERATE", "Generate AI Draft");
        return response;
    }

    @Override
    public SseEmitter streamDraft(AiDraftRequest request) {
        String message = normalizeMessage(request);
        String sessionId = resolveSessionId(request);
        Claims claims = AppUserContext.get();
        SseEmitter emitter = new SseEmitter(90_000L);

        CompletableFuture.runAsync(() -> {
            try {
                if (claims != null) {
                    AppUserContext.set(claims);
                }
                streamDraftInternal(request, message, sessionId, emitter);
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("智能生成暂时不可用，请稍后重试。"));
                } catch (Exception ignored) {
                    // Ignore send failure during error handling.
                }
                emitter.complete();
            } finally {
                AppUserContext.remove();
            }
        });

        return emitter;
    }

    @Override
    public void clearSession(String sessionId) {
        aiDraftSessionStore.clear(AppUserContext.getUserId(), sessionId);
    }

    private void streamDraftInternal(AiDraftRequest request, String message, String sessionId, SseEmitter emitter) throws Exception {
        DraftContext context = buildDraftContext(request, message, sessionId);

        boolean fallbackUsed = false;
        try {
            String systemPrompt = aiPromptBuilder.buildDisplaySystemPrompt(context.domainType(), context.today());
            String userPrompt = aiPromptBuilder.buildDisplayUserPrompt(
                    message,
                    context.historyText(),
                    context.currentDraftJson()
            );
            aiClient.streamText(systemPrompt, userPrompt, delta ->
                    sendTokenEvent(emitter, delta));
        } catch (Exception e) {
            log.warn("AI display stream failed, fallback text will be used. sessionId={}, domainType={}, reason={}",
                    sessionId, context.domainType(), e.getMessage(), e);
            fallbackUsed = true;
            sendTokenEvent(emitter, fallbackDisplayReply(context));
        }

        AiDraftResponse response = generateDraftResponse(context, fallbackUsed);
        saveContext(context, response);
        operationLogService.log("AI", "GENERATE", "Generate AI Draft");
        sendEvent(emitter, "final", response);
    }

    private AiDraftResponse generateDraftResponse(DraftContext context) {
        return generateDraftResponse(context, false);
    }

    private AiDraftResponse generateDraftResponse(DraftContext context, boolean displayFallbackUsed) {
        AiProjectDraft draft;
        boolean fallbackUsed = displayFallbackUsed;
        try {
            String systemPrompt = aiPromptBuilder.buildSystemPrompt(context.domainType(), context.today());
            String userPrompt = aiPromptBuilder.buildUserPrompt(
                    context.message(),
                    context.domainType(),
                    context.historyText(),
                    context.currentDraftJson()
            );
            String content = aiClient.createDraftJson(systemPrompt, userPrompt);
            draft = aiDraftSanitizer.parseDraft(content);
        } catch (Exception e) {
            log.warn("AI draft generation failed, fallback will be used. sessionId={}, domainType={}, reason={}",
                    context.sessionId(), context.domainType(), e.getMessage(), e);
            fallbackUsed = true;
            draft = context.fallbackDraft();
            addWarning(draft, "智能生成暂时不可用，已使用基础规划方式生成草案。");
        }

        AiProjectDraft sanitizedDraft = aiDraftSanitizer.sanitizeForPreview(
                draft,
                context.fallbackDraft(),
                context.domainType(),
                context.today()
        );
        aiTemplateService.alignSettingWithUserAvailability(sanitizedDraft, context.message());
        aiAuditService.recordDraft(context.sessionId(), context.message(), context.domainType(), sanitizedDraft, fallbackUsed);

        AiDraftResponse response = new AiDraftResponse();
        response.setSessionId(context.sessionId());
        response.setStage(resolveStage(context));
        response.setDomainType(context.domainType());
        response.setFallbackUsed(fallbackUsed);
        response.setDraft(sanitizedDraft);
        response.setReply(buildReply(context, fallbackUsed));
        return response;
    }

    private DraftContext buildDraftContext(AiDraftRequest request, String message, String sessionId) {
        LocalDate today = LocalDate.now();
        Integer userId = AppUserContext.getUserId();
        AiDraftSessionStore.SessionState state = aiDraftSessionStore.find(userId, sessionId);
        List<AiChatMessage> history = resolveHistory(request, state);
        AiProjectDraft currentDraft = resolveCurrentDraft(request, state);
        DomainType domainType = resolveDomainType(message, currentDraft, state, history);
        AiProjectDraft fallbackDraft = currentDraft == null
                ? aiTemplateService.buildFallbackDraft(message, domainType, today)
                : currentDraft;

        return new DraftContext(
                userId,
                sessionId,
                message,
                today,
                domainType,
                history,
                currentDraft,
                fallbackDraft,
                buildHistoryText(history),
                buildCurrentDraftJson(currentDraft)
        );
    }

    private List<AiChatMessage> resolveHistory(AiDraftRequest request, AiDraftSessionStore.SessionState state) {
        if (request != null && request.getMessages() != null && !request.getMessages().isEmpty()) {
            return normalizeHistory(request.getMessages());
        }
        if (state != null && state.getMessages() != null) {
            return normalizeHistory(state.getMessages());
        }
        return List.of();
    }

    private AiProjectDraft resolveCurrentDraft(AiDraftRequest request, AiDraftSessionStore.SessionState state) {
        if (request != null && request.getCurrentDraft() != null) {
            return request.getCurrentDraft();
        }
        if (state != null) {
            return state.getLatestDraft();
        }
        return null;
    }

    private DomainType resolveDomainType(String message,
                                         AiProjectDraft currentDraft,
                                         AiDraftSessionStore.SessionState state,
                                         List<AiChatMessage> history) {
        if (currentDraft != null && currentDraft.getDomainType() != null) {
            return currentDraft.getDomainType();
        }
        if (state != null && state.getDomainType() != null) {
            return state.getDomainType();
        }
        StringBuilder text = new StringBuilder();
        history.forEach(item -> text.append(item.getContent()).append('\n'));
        text.append(message);
        return aiTemplateService.detectDomain(text.toString());
    }

    private List<AiChatMessage> normalizeHistory(List<AiChatMessage> messages) {
        List<AiChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            AiChatMessage normalized = new AiChatMessage();
            normalized.setRole("assistant".equals(message.getRole()) ? "assistant" : "user");
            normalized.setContent(trim(message.getContent().trim(), 1000));
            result.add(normalized);
        }
        if (result.size() <= 12) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - 12, result.size()));
    }

    private String buildHistoryText(List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (AiChatMessage message : history) {
            String role = "assistant".equals(message.getRole()) ? "AI" : "用户";
            builder.append(role)
                    .append("：")
                    .append(message.getContent())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String buildCurrentDraftJson(AiProjectDraft draft) {
        if (draft == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            return "";
        }
    }

    private AiDraftStage resolveStage(DraftContext context) {
        boolean hasPreviousDraft = context.currentDraft() != null;
        boolean hasAvailability = aiTemplateService.containsExplicitDailyAvailability(context.message());
        if (!hasPreviousDraft) {
            return hasAvailability ? AiDraftStage.SETTING_REVIEW : AiDraftStage.TASK_REVIEW;
        }
        return AiDraftStage.READY_TO_APPLY;
    }

    private String buildReply(DraftContext context, boolean fallbackUsed) {
        if (fallbackUsed) {
            return "智能生成暂时不可用，已基于当前上下文保留或生成基础草案。";
        }
        if (context.currentDraft() == null && !aiTemplateService.containsExplicitDailyAvailability(context.message())) {
            return "已先生成 Task 草案。你可以继续告诉我需要删除、修改或补充哪些任务，以及每天大概能投入多少时间。";
        }
        if (context.currentDraft() == null) {
            return "已生成 Project、Task 和 Setting 草案。你可以继续补充修改意见，或确认创建。";
        }
        return "已根据你的补充更新同一个 Project 草案。";
    }

    private String fallbackDisplayReply(DraftContext context) {
        if (context.currentDraft() == null) {
            return "我会先理解目标领域并拆出一版可执行 Task 草案，再根据你补充的时间和节奏信息完善计划设置。";
        }
        return "我会基于当前草案继续调整同一个项目，保留仍然合理的任务和设置，只修改你这次补充到的部分。";
    }

    private void saveContext(DraftContext context, AiDraftResponse response) {
        aiDraftSessionStore.save(
                context.userId(),
                context.sessionId(),
                context.history(),
                context.message(),
                response.getReply(),
                response.getDraft(),
                response.getDomainType()
        );
    }

    private void addWarning(AiProjectDraft draft, String warning) {
        if (draft == null || warning == null || warning.isBlank()) {
            return;
        }
        if (draft.getWarnings() == null) {
            draft.setWarnings(new ArrayList<>());
        }
        draft.getWarnings().add(warning);
    }

    private void sendTokenEvent(SseEmitter emitter, String text) {
        sendEvent(emitter, "token", Map.of("text", text));
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            throw new IllegalStateException("发送 AI 流式事件失败", e);
        }
    }

    private String normalizeMessage(AiDraftRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("请输入想要规划的目标");
        }
        return request.getMessage().trim();
    }

    private String resolveSessionId(AiDraftRequest request) {
        if (request != null && request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId().trim();
        }
        return UUID.randomUUID().toString();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record DraftContext(Integer userId,
                                String sessionId,
                                String message,
                                LocalDate today,
                                DomainType domainType,
                                List<AiChatMessage> history,
                                AiProjectDraft currentDraft,
                                AiProjectDraft fallbackDraft,
                                String historyText,
                                String currentDraftJson) {
    }
}
