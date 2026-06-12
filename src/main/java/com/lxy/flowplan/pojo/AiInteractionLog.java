package com.lxy.flowplan.pojo;

import com.lxy.flowplan.dto.ai.DomainType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_interaction_log")
public class AiInteractionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", length = 32)
    private DomainType domainType;

    @Column(name = "project_id")
    private Integer projectId;

    @Column(name = "fallback_used", nullable = false)
    private Boolean fallbackUsed = false;

    @Lob
    @Column(name = "user_message")
    private String userMessage;

    @Lob
    @Column(name = "draft_json")
    private String draftJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
