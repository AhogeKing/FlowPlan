package com.lxy.flowplan.service.Impl;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.mapper.TaskMapper;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class TaskServiceImpl implements TaskService {
    private static final Set<String> ALLOWED_TASK_STATUS = Set.of("NOT_STARTED", "IN_PROGRESS", "DONE");

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    public TaskServiceImpl(TaskMapper taskMapper,
                           ProjectMapper projectMapper,
                           OperationLogService operationLogService) {
        this.taskMapper = taskMapper;
        this.projectMapper = projectMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public void addTask(Integer projectId, Task task) {
        // 先确认 Project 属于当前用户，再允许新增 Task。
        Project project = getCurrentUserProject(projectId);
        validateTaskForSave(task);
        validateDependency(projectId, null, task.getDependencyTaskId());

        // 这些字段由服务端决定，避免前端伪造归属。
        task.setId(null);
        task.setProjectId(projectId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        refreshTaskStatus(task);

        taskMapper.insert(task);
        markProjectNeedReplan(project);
        operationLogService.log("TASK", "CREATE", "Create Task: " + task.getTitle());
    }

    @Override
    public List<Task> listTasksByProject(Integer projectId) {
        // 查询前也做归属校验，避免越权读取其他用户的 Task。
        Project project = getCurrentUserProject(projectId);
        List<Task> tasks = taskMapper.selectListByProjectId(projectId);
        boolean needReplan = false;

        for (Task task : tasks) {
            if (refreshTaskStatusIfChanged(task)) {
                needReplan = true;
            }
        }
        if (needReplan) {
            markProjectNeedReplan(project);
        }
        return tasks;
    }

    @Override
    public void updateTask(Integer projectId, Integer taskId, Task task) {
        // 更新必须同时满足 Project 属于当前用户、Task 属于当前 Project。
        Project project = getCurrentUserProject(projectId);
        if (taskId == null) {
            throw new IllegalArgumentException("Task id 不能为空");
        }
        Task dbTask = taskMapper.selectByIdAndProjectId(taskId, projectId);
        if (dbTask == null) {
            throw new IllegalArgumentException("Task 不存在或不属于当前 Project");
        }
        validateTaskForSave(task);
        validateDependency(projectId, taskId, task.getDependencyTaskId());

        String oldStatus = dbTask.getStatus();
        Boolean oldDoneFlag = dbTask.getDoneFlag();

        // 影响排期的关键字段变化后，需要提示后续计划重新生成。
        boolean needReplan = !Objects.equals(dbTask.getWeight(), task.getWeight())
                || !Objects.equals(dbTask.getMinSessionMinutes(), task.getMinSessionMinutes())
                || !Objects.equals(dbTask.getBeginDate(), task.getBeginDate())
                || !Objects.equals(dbTask.getDeadline(), task.getDeadline())
                || !Objects.equals(dbTask.getDependencyTaskId(), task.getDependencyTaskId())
                || !Objects.equals(dbTask.getDoneFlag(), task.getDoneFlag())
                || !Objects.equals(dbTask.getStatus(), task.getStatus());

        dbTask.setTitle(task.getTitle());
        dbTask.setDescription(task.getDescription());
        dbTask.setWeight(task.getWeight());
        dbTask.setMinSessionMinutes(task.getMinSessionMinutes());
        dbTask.setBeginDate(task.getBeginDate());
        dbTask.setDeadline(task.getDeadline());
        dbTask.setDependencyTaskId(task.getDependencyTaskId());
        dbTask.setDoneFlag(task.getDoneFlag());
        dbTask.setStatus(task.getStatus());
        refreshTaskStatus(dbTask);
        needReplan = needReplan
                || !Objects.equals(oldStatus, dbTask.getStatus())
                || !Objects.equals(oldDoneFlag, dbTask.getDoneFlag());
        dbTask.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(dbTask);

        if (needReplan) {
            markProjectNeedReplan(project);
        }
        operationLogService.log("TASK", "UPDATE", "Update Task: " + dbTask.getTitle());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Integer projectId, Integer taskId) {
        // 删除前先查一次，给不存在或越权场景返回清晰错误。
        Project project = getCurrentUserProject(projectId);
        if (taskId == null) {
            throw new IllegalArgumentException("Task id 不能为空");
        }

        Task task = taskMapper.selectByIdAndProjectId(taskId, projectId);
        if (task == null) {
            throw new IllegalArgumentException("Task 不存在或不属于当前 Project");
        }

        taskMapper.deleteByIdAndProjectId(taskId, projectId);
        markProjectNeedReplan(project);
        operationLogService.log("TASK", "DELETE", "Delete Task: " + task.getTitle());
    }

    private Project getCurrentUserProject(Integer projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        // 所有 Task 操作都通过 Project.userId 做用户数据隔离。
        Project project = projectMapper.selectByIdAndUserId(projectId, AppUserContext.getUserId());
        if (project == null) {
            throw new IllegalArgumentException("Project 不存在或不属于当前用户");
        }
        return project;
    }

    private void markProjectNeedReplan(Project project) {
        // Task 结构或工期变化会影响 Project 的后续计划。
        project.setNeedReplan(true);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
    }

    private void validateDependency(Integer projectId, Integer currentTaskId, Integer dependencyTaskId) {
        // V1 只做基础合法性校验：依赖存在、同项目、不能依赖自己。
        if (dependencyTaskId == null) {
            return;
        }
        if (Objects.equals(currentTaskId, dependencyTaskId)) {
            throw new IllegalArgumentException("Task 不能依赖自己");
        }
        boolean exists = taskMapper.existsByIdAndProjectId(dependencyTaskId, projectId);
        if (!exists) {
            throw new IllegalArgumentException("依赖 Task 不存在或不属于当前 Project");
        }
    }

    private void validateTaskForSave(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("Task 不能为空");
        }

        task.setTitle(normalizeRequired(task.getTitle()));
        if (task.getTitle().length() > 100) {
            throw new IllegalArgumentException("Task 标题长度不能超过 100 个字符");
        }

        if (task.getWeight() == null) {
            task.setWeight(1);
        }
        if (task.getWeight() <= 0) {
            throw new IllegalArgumentException("Task weight 必须大于 0");
        }

        if (task.getMinSessionMinutes() != null && task.getMinSessionMinutes() <= 0) {
            throw new IllegalArgumentException("Task minSessionMinutes 必须大于 0");
        }

        if (task.getBeginDate() != null
                && task.getDeadline() != null
                && task.getBeginDate().isAfter(task.getDeadline())) {
            throw new IllegalArgumentException("Task beginDate 不能晚于 deadline");
        }

        if (task.getDoneFlag() == null) {
            task.setDoneFlag(false);
        }

        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("NOT_STARTED");
        }
        task.setStatus(task.getStatus().trim());
        if (!ALLOWED_TASK_STATUS.contains(task.getStatus())) {
            throw new IllegalArgumentException("Task status 只能是 NOT_STARTED、IN_PROGRESS 或 DONE");
        }
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task 标题" + "不能为空");
        }
        return value.trim();
    }

    private boolean refreshTaskStatusIfChanged(Task task) {
        String oldStatus = task.getStatus();
        Boolean oldDoneFlag = task.getDoneFlag();
        refreshTaskStatus(task);
        boolean changed = !Objects.equals(oldStatus, task.getStatus())
                || !Objects.equals(oldDoneFlag, task.getDoneFlag());
        if (changed) {
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        return changed;
    }

    private void refreshTaskStatus(Task task) {
        if (task == null) {
            return;
        }
        if (Boolean.TRUE.equals(task.getDoneFlag()) || "DONE".equals(task.getStatus())) {
            task.setDoneFlag(true);
            task.setStatus("DONE");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate deadline = task.getDeadline();
        if (deadline != null && today.isAfter(deadline)) {
            task.setDoneFlag(true);
            task.setStatus("DONE");
            return;
        }

        task.setDoneFlag(false);
        LocalDate beginDate = task.getBeginDate();
        if (beginDate == null || !today.isBefore(beginDate)) {
            task.setStatus("IN_PROGRESS");
            return;
        }
        task.setStatus("NOT_STARTED");
    }
}
