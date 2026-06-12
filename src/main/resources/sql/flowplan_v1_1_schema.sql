-- FlowPlan V1.1 schema
-- 单 Project、预算式、可解释、可重排的宏观计划生成器
-- MySQL 8.x
-- 从零重建数据库：会删除已有 flowplan 库，并重新创建空表。
-- 本脚本不插入任何业务数据，所有 AUTO_INCREMENT 都会从 1 重新开始。

SET NAMES utf8mb4;

DROP DATABASE IF EXISTS flowplan;
CREATE DATABASE flowplan
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
USE flowplan;

CREATE TABLE app_user
(
    id       INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)                           NOT NULL,
    password VARCHAR(255)                          NOT NULL,
    email    VARCHAR(100)                          NOT NULL,
    role     VARCHAR(30) DEFAULT 'USER'            NOT NULL COMMENT '权限角色：USER 普通用户，ADMIN 管理员',
    reg_time DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE project
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT                                   NOT NULL,
    name        VARCHAR(100)                          NOT NULL,
    description TEXT                                  NULL,
    begin_date  DATE                                  NULL,
    finish_date DATE                                  NULL,
    deadline    DATE                                  NOT NULL,

    status      VARCHAR(30) DEFAULT 'NOT_STARTED'     NOT NULL COMMENT '项目状态：NOT_STARTED IN_PROGRESS OVERDUE DONE',
    risk_level  VARCHAR(30) DEFAULT 'OK'              NOT NULL COMMENT 'V1.1 中表示排期压力：RELAXED OK PRESSURE，不表示真实完成风险',
    need_replan TINYINT(1)  DEFAULT 0                 NOT NULL COMMENT '关键字段变化后标记为 1，提示系统重新生成计划',

    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_project_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'OVERDUE', 'DONE')),
    CONSTRAINT ck_project_risk_level CHECK (risk_level IN ('RELAXED', 'OK', 'PRESSURE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_setting
(
    id                     INT AUTO_INCREMENT PRIMARY KEY,
    user_id                INT         NOT NULL,
    scope                  VARCHAR(10) NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL 全局设置，LOCAL Project 专属设置',
    project_id             INT         NULL,

    base_daily_minutes     INT         NOT NULL DEFAULT 120 COMMENT '基准工作日时长，单位：分钟',

    mon_ratio              INT         NOT NULL DEFAULT 100,
    tue_ratio              INT         NOT NULL DEFAULT 100,
    wed_ratio              INT         NOT NULL DEFAULT 100,
    thu_ratio              INT         NOT NULL DEFAULT 100,
    fri_ratio              INT         NOT NULL DEFAULT 100,
    sat_ratio              INT         NOT NULL DEFAULT 100,
    sun_ratio              INT         NOT NULL DEFAULT 100,

    daily_min_minutes      INT         NOT NULL DEFAULT 20 COMMENT '每日计划强度下限，低于该值视为偏松或不排期',
    daily_max_minutes      INT         NOT NULL DEFAULT 120 COMMENT '每日计划强度上限，超过该值视为有排期压力',

    task_min_count_per_day INT         NOT NULL DEFAULT 1 COMMENT '每日任务数量下限，任务不足时可忽略',
    task_max_count_per_day INT         NOT NULL DEFAULT 4 COMMENT '每日任务数量上限',

    min_plan_item_minutes  INT         NOT NULL DEFAULT 20 COMMENT '单个计划项推荐最小时长',
    max_plan_item_minutes  INT         NOT NULL DEFAULT 120 COMMENT '单个计划项推荐最大时长',

    time_block_minutes     INT         NOT NULL DEFAULT 10 COMMENT '计划项时间取整单位',
    balance_factor         INT         NOT NULL DEFAULT 50 COMMENT '均衡系数：越高越倾向任务分散',

    created_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_plan_setting_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_setting_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,

    CONSTRAINT ck_plan_setting_scope CHECK (scope IN ('GLOBAL', 'LOCAL')),
    CONSTRAINT ck_plan_setting_scope_project CHECK (
        (scope = 'GLOBAL' AND project_id IS NULL)
        OR
        (scope = 'LOCAL' AND project_id IS NOT NULL)
    ),
    CONSTRAINT ck_plan_setting_daily_minutes CHECK (
        base_daily_minutes > 0
        AND daily_min_minutes >= 0
        AND daily_max_minutes >= daily_min_minutes
    ),
    CONSTRAINT ck_plan_setting_ratios CHECK (
        mon_ratio >= 0 AND tue_ratio >= 0 AND wed_ratio >= 0 AND thu_ratio >= 0
        AND fri_ratio >= 0 AND sat_ratio >= 0 AND sun_ratio >= 0
    ),
    CONSTRAINT ck_plan_setting_task_count CHECK (
        task_min_count_per_day >= 0
        AND task_max_count_per_day >= task_min_count_per_day
    ),
    CONSTRAINT ck_plan_setting_item_minutes CHECK (
        min_plan_item_minutes > 0
        AND max_plan_item_minutes >= min_plan_item_minutes
        AND time_block_minutes > 0
    ),
    CONSTRAINT ck_plan_setting_balance CHECK (balance_factor BETWEEN 0 AND 100)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- 每个 Project 最多一份 LOCAL setting。
-- MySQL UNIQUE 允许多个 NULL，因此不会影响 GLOBAL setting 的 project_id = NULL。
CREATE UNIQUE INDEX uk_plan_setting_project
    ON plan_setting (project_id);

CREATE INDEX idx_plan_setting_user_scope
    ON plan_setting (user_id, scope);

CREATE TABLE task
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    project_id          INT                                   NOT NULL,
    title               VARCHAR(100)                          NOT NULL,
    description         TEXT                                  NULL,

    weight              INT         DEFAULT 1                 NOT NULL COMMENT '任务在 Project 时间预算中的分配权重',
    min_session_minutes INT                                   NULL COMMENT '单次安排该任务的建议最低分钟数；为空则使用 plan_setting.min_plan_item_minutes',

    begin_date          DATE                                  NULL,
    deadline            DATE                                  NULL COMMENT 'Task 自身截止日期，可为空；Project deadline 仍为总截止日期',
    dependency_task_id  INT                                   NULL COMMENT '前置依赖任务；为空表示无依赖',

    done_flag           TINYINT(1)  DEFAULT 0                 NOT NULL COMMENT '用户是否手动标记完成',
    status              VARCHAR(30) DEFAULT 'NOT_STARTED'     NOT NULL COMMENT '简化状态：NOT_STARTED IN_PROGRESS DONE；可由 done_flag 与日期推导',

    created_at          DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_dependency FOREIGN KEY (dependency_task_id) REFERENCES task (id) ON DELETE SET NULL,
    CONSTRAINT ck_task_weight CHECK (weight > 0),
    CONSTRAINT ck_task_min_session CHECK (min_session_minutes IS NULL OR min_session_minutes > 0),
    CONSTRAINT ck_task_done_flag CHECK (done_flag IN (0, 1)),
    CONSTRAINT ck_task_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'DONE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_task_project_deadline
    ON task (project_id, deadline);

CREATE INDEX idx_task_project_done
    ON task (project_id, done_flag);

CREATE INDEX idx_task_dependency
    ON task (dependency_task_id);

CREATE TABLE daily_plan
(
    id                        INT AUTO_INCREMENT PRIMARY KEY,
    project_id                INT                            NOT NULL,
    plan_date                 DATE                           NOT NULL,
    total_recommended_minutes INT         DEFAULT 0          NOT NULL COMMENT '当天计划总量，由计划项汇总',
    total_actual_minutes      INT         DEFAULT 0          NOT NULL COMMENT '当天实际投入，由打卡汇总',
    status                    VARCHAR(30) DEFAULT 'NOT_DONE' NOT NULL COMMENT 'NOT_DONE PARTIAL_DONE FULL_DONE',

    CONSTRAINT uk_project_plan_date UNIQUE (project_id, plan_date),
    CONSTRAINT fk_daily_plan_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT ck_daily_plan_minutes CHECK (total_recommended_minutes >= 0 AND total_actual_minutes >= 0),
    CONSTRAINT ck_daily_plan_status CHECK (status IN ('NOT_DONE', 'PARTIAL_DONE', 'FULL_DONE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE daily_plan_item
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    plan_id             INT                            NOT NULL,
    task_id             INT                            NOT NULL,
    recommended_minutes INT         DEFAULT 0          NOT NULL,
    actual_minutes      INT         DEFAULT 0          NOT NULL,
    sort_order          INT         DEFAULT 0          NOT NULL,
    status              VARCHAR(30) DEFAULT 'NOT_DONE' NOT NULL COMMENT 'NOT_DONE PARTIAL_DONE FULL_DONE',
    reason              VARCHAR(255)                   NULL,

    CONSTRAINT uk_plan_item_id_task_id UNIQUE (id, task_id),
    CONSTRAINT uk_plan_task UNIQUE (plan_id, task_id),
    CONSTRAINT fk_plan_item_plan FOREIGN KEY (plan_id) REFERENCES daily_plan (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_item_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
    CONSTRAINT ck_plan_item_minutes CHECK (recommended_minutes >= 0 AND actual_minutes >= 0),
    CONSTRAINT ck_plan_item_status CHECK (status IN ('NOT_DONE', 'PARTIAL_DONE', 'FULL_DONE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE checkin_record
(
    id                INT AUTO_INCREMENT PRIMARY KEY,
    plan_item_id      INT                                NOT NULL,
    task_id           INT                                NOT NULL,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    checkin_date      DATE                               NOT NULL,
    completed_minutes INT      DEFAULT 0                 NOT NULL,
    note              TEXT                               NULL,

    CONSTRAINT uk_checkin_plan_item UNIQUE (plan_item_id),
    CONSTRAINT fk_checkin_plan_item_task FOREIGN KEY (plan_item_id, task_id)
        REFERENCES daily_plan_item (id, task_id) ON DELETE CASCADE,
    CONSTRAINT fk_checkin_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
    CONSTRAINT ck_checkin_minutes CHECK (completed_minutes >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_checkin_task_id
    ON checkin_record (task_id);

CREATE TABLE operation_log
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT                                NOT NULL,
    username        VARCHAR(50)                        NOT NULL,
    module          VARCHAR(30)                        NOT NULL,
    operation_type  VARCHAR(30)                        NOT NULL,
    description     VARCHAR(255)                       NULL,
    request_method  VARCHAR(20)                        NULL,
    request_url     VARCHAR(255)                       NULL,
    ip              VARCHAR(64)                        NULL,
    status          VARCHAR(30) DEFAULT 'SUCCESS'      NOT NULL,
    error_message   TEXT                               NULL,
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT fk_operation_log_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_operation_log_target
    ON operation_log (module, operation_type);

CREATE INDEX idx_operation_log_user_created
    ON operation_log (user_id, create_time);
