package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.Project;

import java.util.List;

public interface ProjectService {
    // 新增当前用户的 Project，并保证同一用户下名称不重复。
    void addProject(Project project);

    // 新增当前用户的 Project，并返回数据库生成 id 后的对象，供内部编排流程继续使用。
    Project createProject(Project project);

    // 只列出当前登录用户自己的 Project。
    List<Project> listCurrentUserProject();

    // 修改当前用户的某个 Project，关键排期字段变化时标记 needReplan。
    void updateProject(Project project);

    // 删除当前用户的某个 Project；数据库会级联删除其 Task 和 Plan。
    void deleteProject(Integer projectId);
}
