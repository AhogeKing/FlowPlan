DROP TABLE IF EXISTS operation_log;

CREATE TABLE operation_log
(
    id             INT PRIMARY KEY AUTO_INCREMENT,
    user_id        INT         NOT NULL,
    username       VARCHAR(50) NOT NULL,
    module         VARCHAR(30) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    description    VARCHAR(255),
    request_method VARCHAR(20),
    request_url    VARCHAR(255),
    ip             VARCHAR(64),
    status         VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    error_message  TEXT,
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_operation_log_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_operation_log_user_created
    ON operation_log (user_id, create_time);

CREATE INDEX idx_operation_log_module_operation
    ON operation_log (module, operation_type);
