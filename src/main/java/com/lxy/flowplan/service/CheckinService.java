package com.lxy.flowplan.service;

import com.lxy.flowplan.pojo.CheckinRequest;
import com.lxy.flowplan.pojo.CheckinRecord;
import com.lxy.flowplan.pojo.PlanDetail;

import java.util.List;

public interface CheckinService {

    PlanDetail checkinPlanItem(Integer projectId, Integer planItemId, CheckinRequest request);

    PlanDetail deleteCheckin(Integer projectId, Integer planItemId);

    List<CheckinRecord> listProjectCheckins(Integer projectId);

    List<CheckinRecord> listTaskCheckins(Integer projectId, Integer taskId);
}
