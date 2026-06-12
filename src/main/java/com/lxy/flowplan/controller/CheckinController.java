package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.CheckinRequest;
import com.lxy.flowplan.pojo.CheckinRecord;
import com.lxy.flowplan.pojo.PlanDetail;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.CheckinService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/project/{projectId}")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @PostMapping("/plan/item/{planItemId}/checkin")
    public Result<PlanDetail> checkinPlanItem(@PathVariable Integer projectId,
                                              @PathVariable Integer planItemId,
                                              @RequestBody(required = false) CheckinRequest request) {
        return Result.success(checkinService.checkinPlanItem(projectId, planItemId, request));
    }

    @DeleteMapping("/plan/item/{planItemId}/checkin")
    public Result<PlanDetail> deleteCheckin(@PathVariable Integer projectId,
                                            @PathVariable Integer planItemId) {
        return Result.success(checkinService.deleteCheckin(projectId, planItemId));
    }

    @GetMapping("/checkin")
    public Result<List<CheckinRecord>> listProjectCheckins(@PathVariable Integer projectId) {
        return Result.success(checkinService.listProjectCheckins(projectId));
    }

    @GetMapping("/task/{taskId}/checkin")
    public Result<List<CheckinRecord>> listTaskCheckins(@PathVariable Integer projectId,
                                                        @PathVariable Integer taskId) {
        return Result.success(checkinService.listTaskCheckins(projectId, taskId));
    }
}
