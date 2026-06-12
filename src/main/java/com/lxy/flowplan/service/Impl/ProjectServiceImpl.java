package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final OperationLogService operationLogService;

    public ProjectServiceImpl(ProjectMapper projectMapper,
                              OperationLogService operationLogService) {
        this.projectMapper = projectMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    public void addProject(Project project) {
        createProject(project);
    }

    @Override
    public Project createProject(Project project) {
        Integer appUserId = AppUserContext.getUserId();
        validateProjectForSave(project, false);

        // Project 名称只在当前用户范围内唯一，不同用户可以使用同名项目。
        if (projectMapper.existsByNameAndUserId(project.getName(), appUserId, null)) {
            throw new IllegalArgumentException("项目名称: " + "'" + project.getName() + "'" + " 已存在");
        }

        // 归属和时间戳由服务端写入，避免前端伪造 userId。
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        project.setUserId(appUserId);
        refreshProjectStatus(project);

        projectMapper.insert(project);
        operationLogService.log("PROJECT", "CREATE", "Create Project: " + project.getName());
        return project;
    }

    @Override
    public List<Project> listCurrentUserProject() {
        // 列表接口始终按当前用户隔离，排序让新创建的项目优先出现。
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getUserId, AppUserContext.getUserId())
                        .orderByDesc(Project::getCreatedAt)
        );

        projects.forEach(this::refreshProjectStatusIfChanged);
        return projects;
    }

    @Override
    public void updateProject(Project project) {
        Integer appUserId = AppUserContext.getUserId();
        validateProjectForSave(project, true);
        Project dbProject = getCurrentUserProject(project.getId());

        // 更新名称时排除当前 Project 自身，再检查是否撞到同用户的其它 Project。
        if (projectMapper.existsByNameAndUserId(project.getName(), appUserId, project.getId())) {
            throw new IllegalArgumentException("项目名称: " + "'" + project.getName() + "'" + " 已存在");
        }

        dbProject.setName(project.getName());
        dbProject.setDescription(project.getDescription());
        dbProject.setUpdatedAt(LocalDateTime.now());

        if (!Objects.equals(dbProject.getBeginDate(), project.getBeginDate())) {
            dbProject.setNeedReplan(true);
        }
        dbProject.setBeginDate(project.getBeginDate());

        if (!Objects.equals(dbProject.getDeadline(), project.getDeadline())) {
            dbProject.setNeedReplan(true);
        }
        dbProject.setDeadline(project.getDeadline());
        refreshProjectStatus(dbProject);

        projectMapper.updateById(dbProject);
        operationLogService.log("PROJECT", "UPDATE", "Update Project: " + dbProject.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Integer projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        getCurrentUserProject(projectId);
        projectMapper.deleteByIdAndUserId(projectId, AppUserContext.getUserId());
        operationLogService.log("PROJECT", "DELETE", "Delete Project: " + projectId);
    }

    private Project getCurrentUserProject(Integer projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        // 统一封装 Project 归属检查，后续方法不再单独信任 projectId。
        Project project = projectMapper.selectByIdAndUserId(projectId, AppUserContext.getUserId());
        if (project == null) {
            throw new IllegalArgumentException("Project 不存在或不属于当前用户");
        }
        return project;
    }

    private void validateProjectForSave(Project project, boolean requireId) {
        if (project == null) {
            throw new IllegalArgumentException("Project 不能为空");
        }
        if (requireId && project.getId() == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        project.setName(normalizeRequired(project.getName()));
        if (project.getName().length() > 100) {
            throw new IllegalArgumentException("项目名称长度不能超过 100 个字符");
        }
        if (project.getDeadline() == null) {
            throw new IllegalArgumentException("Project deadline 不能为空");
        }
        if (project.getBeginDate() != null && project.getBeginDate().isAfter(project.getDeadline())) {
            throw new IllegalArgumentException("Project beginDate 不能晚于 deadline");
        }
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("项目名称" + "不能为空");
        }
        return value.trim();
    }

    private void refreshProjectStatusIfChanged(Project project) {
        String oldStatus = project.getStatus();
        refreshProjectStatus(project);
        if (!Objects.equals(oldStatus, project.getStatus())) {
            project.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(project);
        }
    }

    private void refreshProjectStatus(Project project) {
        if (project == null) {
            return;
        }
        if ("DONE".equals(project.getStatus()) || project.getFinishDate() != null) {
            project.setStatus("DONE");
            return;
        }
        LocalDate today = LocalDate.now();
        LocalDate deadline = project.getDeadline();
        if (deadline != null && today.isAfter(deadline)) {
            project.setStatus("DONE");
            return;
        }

        LocalDate beginDate = project.getBeginDate();
        if (beginDate == null || !today.isBefore(beginDate)) {
            project.setStatus("IN_PROGRESS");
            return;
        }
        project.setStatus("NOT_STARTED");
    }
}
