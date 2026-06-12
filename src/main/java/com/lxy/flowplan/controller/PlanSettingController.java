package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.PlanSetting;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.PlanSettingService;
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
@RequestMapping("/setting")
public class PlanSettingController {
    private final PlanSettingService planSettingService;

    public PlanSettingController(PlanSettingService planSettingService) {
        this.planSettingService = planSettingService;
    }

    @GetMapping("/list")
    public Result<List<PlanSetting>> listPlanSetting() {
        return Result.success(planSettingService.selectListPlanSetting());
    }

    @GetMapping("/global")
    public Result<PlanSetting> getGlobalPlanSetting() {
        return Result.success(planSettingService.selectGlobalPlanSetting());
    }

    @PutMapping("/global")
    public Result<String> updateGlobalPlanSetting(@RequestBody PlanSetting planSetting) {
        planSettingService.updateGlobalPlanSetting(planSetting);
        return Result.success("Global PlanSetting updated successfully");
    }

    @PostMapping("/global/reset")
    public Result<String> resetGlobalPlanSetting() {
        planSettingService.resetGlobalPlanSetting();
        return Result.success("Global PlanSetting reset successfully");
    }

    @GetMapping("/project/{projectId}/local")
    public Result<PlanSetting> getLocalPlanSetting(@PathVariable Integer projectId) {
        return Result.success(planSettingService.selectLocalPlanSetting(projectId));
    }

    @PostMapping("/project/{projectId}/local")
    public Result<String> addLocalPlanSetting(@PathVariable Integer projectId,
                                             @RequestBody PlanSetting planSetting) {
        planSettingService.addLocalPlanSetting(projectId, planSetting);
        return Result.success("Local PlanSetting added successfully");
    }

    @PutMapping("/project/{projectId}/local")
    public Result<String> updateLocalPlanSetting(@PathVariable Integer projectId,
                                                @RequestBody PlanSetting planSetting) {
        planSettingService.updateLocalPlanSetting(projectId, planSetting);
        return Result.success("Local PlanSetting updated successfully");
    }

    @DeleteMapping("/project/{projectId}/local")
    public Result<String> deleteLocalPlanSetting(@PathVariable Integer projectId) {
        planSettingService.deleteLocalPlanSetting(projectId, null);
        return Result.success("Local PlanSetting deleted successfully");
    }
}
