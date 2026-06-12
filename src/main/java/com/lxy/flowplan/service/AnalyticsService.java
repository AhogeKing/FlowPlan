package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.AnalyticsOverview;

public interface AnalyticsService {

    AnalyticsOverview getOverview(Integer projectId, String range);
}
