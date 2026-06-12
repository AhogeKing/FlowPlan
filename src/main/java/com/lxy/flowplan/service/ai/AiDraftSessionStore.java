package com.lxy.flowplan.service.ai;

import com.lxy.flowplan.dto.ai.AiChatMessage;
import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.dto.ai.DomainType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiDraftSessionStore {
    private static final int MAX_MESSAGES = 20;
    private static final int SESSION_TTL_HOURS = 6;

    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState find(Integer userId, String sessionId) {
        evictExpired();
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessions.get(key(userId, sessionId));
    }

    public void save(Integer userId,
                     String sessionId,
                     List<AiChatMessage> baseMessages,
                     String userMessage,
                     String assistantReply,
                     AiProjectDraft latestDraft,
                     DomainType domainType) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        SessionState state = new SessionState();
        state.setSessionId(sessionId);
        state.setUserId(userId);
        state.setMessages(normalizeMessages(baseMessages, userMessage, assistantReply));
        state.setLatestDraft(latestDraft);
        state.setDomainType(domainType);
        state.setUpdatedAt(LocalDateTime.now());
        sessions.put(key(userId, sessionId), state);
    }

    public void clear(Integer userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(key(userId, sessionId));
    }

    private List<AiChatMessage> normalizeMessages(List<AiChatMessage> baseMessages, String userMessage, String assistantReply) {
        List<AiChatMessage> result = new ArrayList<>();
        if (baseMessages != null) {
            baseMessages.stream()
                    .map(this::normalizeMessage)
                    .filter(message -> message != null && !message.getContent().isBlank())
                    .forEach(result::add);
        }
        appendMessage(result, "user", userMessage);
        appendMessage(result, "assistant", assistantReply);

        if (result.size() <= MAX_MESSAGES) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - MAX_MESSAGES, result.size()));
    }

    private void appendMessage(List<AiChatMessage> messages, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        AiChatMessage message = new AiChatMessage();
        message.setRole(role);
        message.setContent(trim(content.trim(), 1200));
        messages.add(message);
    }

    private AiChatMessage normalizeMessage(AiChatMessage source) {
        if (source == null || source.getContent() == null || source.getContent().isBlank()) {
            return null;
        }
        AiChatMessage target = new AiChatMessage();
        target.setRole("assistant".equals(source.getRole()) ? "assistant" : "user");
        target.setContent(trim(source.getContent().trim(), 1200));
        return target;
    }

    private void evictExpired() {
        LocalDateTime expiresBefore = LocalDateTime.now().minusHours(SESSION_TTL_HOURS);
        sessions.entrySet().removeIf(entry -> entry.getValue().getUpdatedAt() != null
                && entry.getValue().getUpdatedAt().isBefore(expiresBefore));
    }

    private String key(Integer userId, String sessionId) {
        return (userId == null ? 0 : userId) + ":" + sessionId.trim();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static class SessionState {
        private Integer userId;
        private String sessionId;
        private List<AiChatMessage> messages = new ArrayList<>();
        private AiProjectDraft latestDraft;
        private DomainType domainType;
        private LocalDateTime updatedAt;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public List<AiChatMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<AiChatMessage> messages) {
            this.messages = messages;
        }

        public AiProjectDraft getLatestDraft() {
            return latestDraft;
        }

        public void setLatestDraft(AiProjectDraft latestDraft) {
            this.latestDraft = latestDraft;
        }

        public DomainType getDomainType() {
            return domainType;
        }

        public void setDomainType(DomainType domainType) {
            this.domainType = domainType;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
