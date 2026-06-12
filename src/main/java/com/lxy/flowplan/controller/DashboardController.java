package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.DashboardTodayVO;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/today")
    public Result<DashboardTodayVO> getTodayDashboard() {
        return Result.success(dashboardService.getTodayDashboard());
    }
}
