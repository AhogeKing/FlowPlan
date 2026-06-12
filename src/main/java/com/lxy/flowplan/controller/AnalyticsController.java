package com.lxy.flowplan.controller;

import com.lxy.flowplan.pojo.AnalyticsOverview;
import com.lxy.flowplan.pojo.Result;
import com.lxy.flowplan.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public Result<AnalyticsOverview> getOverview(@RequestParam(required = false) Integer projectId,
                                                 @RequestParam(defaultValue = "7d") String range) {
        return Result.success(analyticsService.getOverview(projectId, range));
    }
}
