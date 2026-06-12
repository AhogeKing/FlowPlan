
CREATE DATABASE IF NOT EXISTS flowplan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE flowplan;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS checkin_record;
DROP TABLE IF EXISTS daily_plan_item;
DROP TABLE IF EXISTS daily_plan;
DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS app_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 用户是数据归属边界，后续所有项目、操作日志都围绕用户隔离。
CREATE TABLE app_user
(
    id       INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email    VARCHAR(100) NOT NULL UNIQUE,
    role     VARCHAR(30)  NOT NULL DEFAULT 'USER' COMMENT '权限角色：USER 普通用户，ADMIN 管理员',
    reg_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Project 表示一个大目标，是任务拆解、计划生成和风险评估的根对象。
CREATE TABLE project
(
    id          INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT          NOT NULL,

    name        VARCHAR(100) NOT NULL,
    description TEXT,

    begin_date  DATE,
    finish_date DATE,
    deadline    DATE         NOT NULL,

    status      VARCHAR(30)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '实际执行状态，和预测风险分开维护',
    risk_level  VARCHAR(30)  NOT NULL DEFAULT 'SAFE' COMMENT '基于预测完成日期和 deadline 计算的风险等级',

    need_replan TINYINT(1) NOT NULL DEFAULT 0 COMMENT '关键字段变化后标记为 1，提示系统重新生成计划',

    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_user_name
        UNIQUE (user_id, name), -- 同一个用户下 Project 名称不能重复，不同用户可以同名

    CONSTRAINT fk_project_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
            ON DELETE CASCADE -- 用户删除后，清理其所有目标数据
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Task 是 Project 下的可执行拆解项，计划和打卡最终都回流到任务进度。
CREATE TABLE task
(
    id                 INT PRIMARY KEY AUTO_INCREMENT,
    project_id         INT          NOT NULL,

    title              VARCHAR(100) NOT NULL,
    description        TEXT,

    estimated_minutes  INT          NOT NULL DEFAULT 0 COMMENT '总预计投入，使用分钟避免小数小时带来的计算误差',
    completed_minutes  INT          NOT NULL DEFAULT 0 COMMENT '由打卡汇总回填，不作为用户手动输入源',

    begin_date         DATE,
    deadline           DATE         NOT NULL,

    status             VARCHAR(30)  NOT NULL DEFAULT 'NOT_STARTED' COMMENT '实际执行状态，和预测风险分开维护',
    risk_level         VARCHAR(30)  NOT NULL DEFAULT 'SAFE' COMMENT '基于剩余工作量和 deadline 计算的风险等级',

    dependency_task_id INT                   DEFAULT NULL COMMENT 'V2 串行任务的前一个依赖任务',

    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_project
        FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE CASCADE, -- Project 是 Task 的生命周期 owner

    CONSTRAINT fk_task_dependency
        FOREIGN KEY (dependency_task_id) REFERENCES task (id)
            ON DELETE SET NULL -- 依赖任务删除时，不级联删除当前任务，只解除依赖
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- DailyPlan 是系统按项目和日期生成的计划头，同一项目同一天只能有一份。
CREATE TABLE daily_plan
(
    id                        INT PRIMARY KEY AUTO_INCREMENT,
    project_id                INT         NOT NULL,

    plan_date                 DATE        NOT NULL,

    total_recommended_minutes INT         NOT NULL DEFAULT 0 COMMENT '当天计划总量，由计划项汇总',
    total_actual_minutes      INT         NOT NULL DEFAULT 0 COMMENT '当天实际投入，由打卡汇总',

    status                    VARCHAR(30) NOT NULL DEFAULT 'NOT_DONE' COMMENT 'NOT_DONE PARTIAL_DONE FULL_DONE',

    CONSTRAINT fk_daily_plan_project
        FOREIGN KEY (project_id) REFERENCES project (id)
            ON DELETE CASCADE, -- Project 删除后，其生成计划失去意义

    CONSTRAINT uk_project_plan_date
        UNIQUE (project_id, plan_date) -- 防止同一项目同一天生成多份互相冲突的计划
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- DailyPlanItem 是某天对某个任务安排多少时间的明细。
CREATE TABLE daily_plan_item
(
    id                  INT PRIMARY KEY AUTO_INCREMENT,
    plan_id             INT         NOT NULL,
    task_id             INT         NOT NULL,

    recommended_minutes INT         NOT NULL DEFAULT 0,
    actual_minutes      INT         NOT NULL DEFAULT 0,

    sort_order          INT         NOT NULL DEFAULT 0,

    status              VARCHAR(30) NOT NULL DEFAULT 'NOT_DONE',

    reason              VARCHAR(255),

    CONSTRAINT fk_plan_item_plan
        FOREIGN KEY (plan_id) REFERENCES daily_plan (id)
            ON DELETE CASCADE, -- 计划头删除时，计划明细同步删除

    CONSTRAINT fk_plan_item_task
        FOREIGN KEY (task_id) REFERENCES task (id)
            ON DELETE CASCADE, -- 任务删除后，相关计划明细不再保留

    CONSTRAINT uk_plan_task
        UNIQUE (plan_id, task_id) -- 同一份日计划里，一个任务只安排一次
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE UNIQUE INDEX uk_plan_item_id_task_id ON daily_plan_item (id, task_id); -- 给打卡复合外键使用，保证 plan_item_id 和 task_id 是同一条计划项

-- CheckinRecord 是用户对计划项的反馈，驱动任务进度、计划完成度和后续预测。
CREATE TABLE checkin_record
(
    id                INT PRIMARY KEY AUTO_INCREMENT,

    plan_item_id      INT      NOT NULL,
    task_id           INT      NOT NULL,

    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checkin_date      DATE     NOT NULL,

    completed_minutes INT      NOT NULL DEFAULT 0,

    note              TEXT,

    CONSTRAINT fk_checkin_task
        FOREIGN KEY (task_id) REFERENCES task (id)
            ON DELETE CASCADE, -- 保留 task_id 便于直接统计某个任务的累计投入

    CONSTRAINT fk_checkin_plan_item_task
        FOREIGN KEY (plan_item_id, task_id) REFERENCES daily_plan_item (id, task_id)
            ON DELETE CASCADE, -- 数据库层保证打卡的 task_id 和计划项的 task_id 一致

    CONSTRAINT uk_checkin_plan_item
        UNIQUE (plan_item_id) -- V1 限制每个计划项最多一次打卡，避免多次打卡合并复杂度
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- OperationLog 记录关键写操作，用于管理员审计、错误排查和系统维护。
CREATE TABLE operation_log
(
    id              INT PRIMARY KEY AUTO_INCREMENT,

    user_id         INT         NOT NULL,
    username        VARCHAR(50) NOT NULL,

    module          VARCHAR(30) NOT NULL,
    operation_type  VARCHAR(30) NOT NULL,
    description     VARCHAR(255),
    request_method  VARCHAR(20),
    request_url     VARCHAR(255),
    ip              VARCHAR(64),
    status          VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT,

    create_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_operation_log_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
            ON DELETE CASCADE -- 用户删除后，操作日志也随其业务数据清理
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_checkin_task_id ON checkin_record (task_id); -- 统计某个任务的历史打卡和累计投入
CREATE INDEX idx_operation_log_user_created ON operation_log (user_id, create_time); -- 查看某个用户最近做了什么
CREATE INDEX idx_operation_log_target ON operation_log (module, operation_type); -- 按模块和操作类型筛选日志
CREATE INDEX idx_task_project_deadline ON task (project_id, deadline); -- 先按 project_id 定位某个项目的任务，再按 deadline 快速筛选 / 排序

CREATE TABLE user_plan_setting (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL UNIQUE,

    default_daily_minutes INT NOT NULL DEFAULT 120,
    max_tasks_per_day INT NOT NULL DEFAULT 4,
    max_minutes_per_task_per_day INT NOT NULL DEFAULT 60,
    min_minutes_per_task_per_day INT NOT NULL DEFAULT 10,

    prefer_balanced BOOLEAN NOT NULL DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
