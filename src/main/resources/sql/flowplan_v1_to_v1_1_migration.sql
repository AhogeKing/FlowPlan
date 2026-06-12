-- FlowPlan V1 -> V1.1 migration
-- 适用于从当前 V1 表结构迁移到 V1.1 简化语义。
-- 建议先备份数据库；如果已有大量数据，先在测试库执行。

SET NAMES utf8mb4;

-- 1. Project：risk_level 语义改为排期压力，status 简化。
ALTER TABLE project
    MODIFY status VARCHAR(30) DEFAULT 'NOT_STARTED' NOT NULL COMMENT '项目状态：NOT_STARTED IN_PROGRESS OVERDUE DONE',
    MODIFY risk_level VARCHAR(30) DEFAULT 'OK' NOT NULL COMMENT 'V1.1 中表示排期压力：RELAXED OK PRESSURE，不表示真实完成风险';

UPDATE project
SET status = CASE
    WHEN status IN ('FINISHED', 'OVERDUE_FINISHED', 'DONE') THEN 'DONE'
    WHEN status IN ('OVERDUE_IN_PROGRESS', 'OVERDUE') THEN 'OVERDUE'
    WHEN status IN ('IN_PROGRESS') THEN 'IN_PROGRESS'
    ELSE 'NOT_STARTED'
END;

UPDATE project
SET risk_level = CASE
    WHEN risk_level IN ('SAFE', 'WARNING') THEN 'OK'
    WHEN risk_level IN ('DANGER', 'OVERDUE') THEN 'PRESSURE'
    WHEN risk_level IN ('RELAXED', 'OK', 'PRESSURE') THEN risk_level
    ELSE 'OK'
END;

-- 2. PlanSetting：新增设置表。
CREATE TABLE IF NOT EXISTS plan_setting
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

CREATE UNIQUE INDEX uk_plan_setting_project
    ON plan_setting (project_id);

CREATE INDEX idx_plan_setting_user_scope
    ON plan_setting (user_id, scope);

-- 为已有用户创建 GLOBAL 默认设置。
INSERT INTO plan_setting (user_id, scope, project_id)
SELECT u.id, 'GLOBAL', NULL
FROM app_user u
WHERE NOT EXISTS (
    SELECT 1
    FROM plan_setting ps
    WHERE ps.user_id = u.id AND ps.scope = 'GLOBAL'
);

-- 3. Task：去掉 estimated/completed/risk/dependency 语义，新增 weight/min_session/done_flag。
-- 如果你的 MySQL 不支持 DROP CHECK/IF EXISTS 等语法，按错误提示手动调整即可。

ALTER TABLE task
    ADD COLUMN weight INT DEFAULT 1 NOT NULL COMMENT '任务在 Project 时间预算中的分配权重' AFTER description,
    ADD COLUMN min_session_minutes INT NULL COMMENT '单次安排该任务的建议最低分钟数；为空则使用 plan_setting.min_plan_item_minutes' AFTER weight,
    ADD COLUMN done_flag TINYINT(1) DEFAULT 0 NOT NULL COMMENT '用户是否手动标记完成' AFTER deadline;

-- 将旧状态映射到简化状态。
UPDATE task
SET done_flag = CASE
    WHEN status IN ('COMPLETED', 'FINISHED', 'DONE') THEN 1
    ELSE 0
END;

UPDATE task
SET status = CASE
    WHEN done_flag = 1 THEN 'DONE'
    WHEN begin_date IS NOT NULL AND begin_date > CURRENT_DATE THEN 'NOT_STARTED'
    ELSE 'IN_PROGRESS'
END;

ALTER TABLE task
    MODIFY deadline DATE NULL COMMENT 'Task 自身截止日期，可为空；Project deadline 仍为总截止日期',
    MODIFY status VARCHAR(30) DEFAULT 'NOT_STARTED' NOT NULL COMMENT '简化状态：NOT_STARTED IN_PROGRESS DONE；可由 done_flag 与日期推导';

-- 依赖任务和估时风险属于旧语义，V1.1 移除。
-- 注意：如果 Java 实体代码尚未同步修改，先不要执行 DROP COLUMN。
ALTER TABLE task
    DROP FOREIGN KEY fk_task_dependency;

ALTER TABLE task
    DROP COLUMN dependency_task_id,
    DROP COLUMN estimated_minutes,
    DROP COLUMN completed_minutes,
    DROP COLUMN risk_level,
    DROP COLUMN priority;

CREATE INDEX idx_task_project_done
    ON task (project_id, done_flag);

-- 4. DailyPlan / DailyPlanItem / Checkin：保留原结构，仅明确 status 语义。
ALTER TABLE daily_plan
    MODIFY status VARCHAR(30) DEFAULT 'NOT_DONE' NOT NULL COMMENT 'NOT_DONE PARTIAL_DONE FULL_DONE';

ALTER TABLE daily_plan_item
    MODIFY status VARCHAR(30) DEFAULT 'NOT_DONE' NOT NULL COMMENT 'NOT_DONE PARTIAL_DONE FULL_DONE';

-- V1.1 中，checkin_record.completed_minutes 只用于每日实际投入统计，
-- 不再回填 task.completed_minutes，因为 task.completed_minutes 已移除。
