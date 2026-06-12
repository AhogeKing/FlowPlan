package com.lxy.flowplan.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.dto.ai.DomainType;
import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.pojo.AiInteractionLog;
import com.lxy.flowplan.repository.AiInteractionLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AiAuditService {
    private final AiInteractionLogRepository aiInteractionLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.audit-enabled:false}")
    private Boolean auditEnabled;

    public AiAuditService(AiInteractionLogRepository aiInteractionLogRepository, ObjectMapper objectMapper) {
        this.aiInteractionLogRepository = aiInteractionLogRepository;
        this.objectMapper = objectMapper;
    }

    public void recordDraft(String sessionId, String message, DomainType domainType, AiProjectDraft draft, boolean fallbackUsed) {
        if (!Boolean.TRUE.equals(auditEnabled)) {
            return;
        }
        AiInteractionLog log = new AiInteractionLog();
        log.setUserId(AppUserContext.getUserId());
        log.setSessionId(sessionId);
        log.setEventType("DRAFT");
        log.setDomainType(domainType);
        log.setFallbackUsed(fallbackUsed);
        log.setUserMessage(message);
        log.setDraftJson(writeDraftJson(draft));
        log.setCreatedAt(LocalDateTime.now());
        saveQuietly(log);
    }

    public void recordApply(String sessionId, Integer projectId) {
        if (!Boolean.TRUE.equals(auditEnabled)) {
            return;
        }
        AiInteractionLog log = new AiInteractionLog();
        log.setUserId(AppUserContext.getUserId());
        log.setSessionId(sessionId);
        log.setEventType("APPLY");
        log.setProjectId(projectId);
        log.setFallbackUsed(false);
        log.setCreatedAt(LocalDateTime.now());
        saveQuietly(log);
    }

    private String writeDraftJson(AiProjectDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveQuietly(AiInteractionLog log) {
        try {
            if (log.getUserId() != null) {
                aiInteractionLogRepository.save(log);
            }
        } catch (Exception ignored) {
            // AI 审计不能阻塞用户生成计划；缺表或临时数据库错误后续再补记录即可。
        }
    }
}
