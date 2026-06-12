package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/list")
    public Result<List<Project>> list() {
        return Result.success(projectService.listCurrentUserProject());
    }

    @PostMapping("/add")
    public Result<String> addProject(@RequestBody Project project) {
        projectService.addProject(project);
        return Result.success("add a project: " + project.getName());
    }

    @PutMapping("/{id}")
    public Result<String> updateProject(@PathVariable Integer id, @RequestBody Project project) {
        project.setId(id);
        projectService.updateProject(project);
        return Result.success("update a project: " + project.getName());
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteProject(@PathVariable Integer id) {
        projectService.deleteProject(id);
        return Result.success("delete a project: " + id);
    }
}
