CREATE TABLE IF NOT EXISTS ai_interaction_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    session_id VARCHAR(64),
    event_type VARCHAR(32) NOT NULL,
    domain_type VARCHAR(32),
    project_id INT,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    user_message TEXT,
    draft_json LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_log_user_created (user_id, created_at),
    INDEX idx_ai_log_session (session_id),
    CONSTRAINT fk_ai_log_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_log_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL
);
