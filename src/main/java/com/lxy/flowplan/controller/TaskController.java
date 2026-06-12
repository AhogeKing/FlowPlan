package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.service.TaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/project/{projectId}/task")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // 查询当前用户某个 Project 下的所有 Task。
    @GetMapping("/list")
    public Result<List<Task>> listTasks(@PathVariable Integer projectId) {
        return Result.success(taskService.listTasksByProject(projectId));
    }

    // 在指定 Project 下新增 Task，projectId 只从路径读取，不信任请求体。
    @PostMapping("/add")
    public Result<String> addTask(@PathVariable Integer projectId, @RequestBody Task task) {
        taskService.addTask(projectId, task);
        return Result.success("task: '" + task.getTitle() + "' added successfully");
    }

    // 修改指定 Project 下的单个 Task。
    @PutMapping("/{id}")
    public Result<String> updateTask(@PathVariable Integer projectId,
                                     @PathVariable Integer id,
                                     @RequestBody Task task) {
        taskService.updateTask(projectId, id, task);
        return Result.success("task: '" + task.getTitle() + "' updated successfully");
    }

    // 删除指定 Project 下的单个 Task。
    @DeleteMapping("/{id}")
    public Result<String> deleteTask(@PathVariable Integer projectId, @PathVariable Integer id) {
        taskService.deleteTask(projectId, id);
        return Result.success("delete task: " + id);
    }
}
