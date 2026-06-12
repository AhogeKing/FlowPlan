package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.PlanDetail;
import com.lxy.flowplan.pojo.PlanGenerateResult;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.PlanService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/project/{projectId}/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    // 根据当前 Project 下的 Task 重新生成今天之后的计划。
    @PostMapping("/generate")
    public Result<PlanGenerateResult> generatePlan(@PathVariable Integer projectId) {
        return Result.success(planService.generatePlan(projectId));
    }

    // 查看当前用户某个 Project 下已经生成的计划。
    @GetMapping("/list")
    public Result<List<DailyPlan>> listPlans(@PathVariable Integer projectId) {
        return Result.success(planService.listPlans(projectId));
    }

    // 查看某一天的计划和计划明细。
    @GetMapping("/date/{planDate}")
    public Result<PlanDetail> getPlanByDate(@PathVariable Integer projectId,
                                            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate) {
        return Result.success(planService.getPlanByDate(projectId, planDate));
    }

    // 删除某个 Project 下的所有 Plan
    @DeleteMapping("/delete")
    public Result<String> deletePlan(@PathVariable Integer projectId) {
        return Result.success(planService.deleteAllPlanByProjectId(projectId));
    }
}
