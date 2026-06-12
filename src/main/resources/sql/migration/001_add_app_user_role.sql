USE flowplan;

-- 已经创建过数据库和表时，只执行这一条迁移即可。
-- 如果 app_user 已经存在 role 字段，不要重复执行。
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'USER' COMMENT '权限角色：USER 普通用户，ADMIN 管理员'
        AFTER email;
