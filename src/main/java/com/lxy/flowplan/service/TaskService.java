package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.Task;

import java.util.List;

public interface TaskService {
    // 在当前用户的指定 Project 下新增 Task，并触发 Project 重新排期标记。
    void addTask(Integer projectId, Task task);

    // 查询前先校验 Project 归属，再返回该 Project 下的 Task 列表。
    List<Task> listTasksByProject(Integer projectId);

    // 修改 Task 的排期相关字段时，需要重新标记 Project。
    void updateTask(Integer projectId, Integer taskId, Task task);

    // 删除 Task 后，Project 的后续计划也需要重新生成。
    void deleteTask(Integer projectId, Integer taskId);
}
