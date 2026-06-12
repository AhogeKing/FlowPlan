USE flowplan;

-- 限制同一个用户下不能创建同名 Project；不同用户之间允许 Project 同名。
-- 执行前如果已有重复数据，需要先清理重复的 (user_id, name)，否则 ALTER TABLE 会失败。
ALTER TABLE project
    ADD CONSTRAINT uk_project_user_name UNIQUE (user_id, name);
