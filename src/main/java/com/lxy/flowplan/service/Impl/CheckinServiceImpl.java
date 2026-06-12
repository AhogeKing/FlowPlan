package com.lxy.flowplan.service.Impl;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.DailyPlanItemMapper;
import com.lxy.flowplan.mapper.DailyPlanMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.mapper.TaskMapper;
import com.lxy.flowplan.pojo.CheckinRecord;
import com.lxy.flowplan.pojo.CheckinRequest;
import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.DailyPlanItem;
import com.lxy.flowplan.pojo.PlanDetail;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.repository.CheckinRecordRepository;
import com.lxy.flowplan.service.CheckinService;
import com.lxy.flowplan.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CheckinServiceImpl implements CheckinService {
    private static final String STATUS_NOT_DONE = "NOT_DONE";
    private static final String STATUS_PARTIAL_DONE = "PARTIAL_DONE";
    private static final String STATUS_FULL_DONE = "FULL_DONE";

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final DailyPlanMapper dailyPlanMapper;
    private final DailyPlanItemMapper dailyPlanItemMapper;
    private final CheckinRecordRepository checkinRecordRepository;
    private final OperationLogService operationLogService;

    public CheckinServiceImpl(ProjectMapper projectMapper,
                              TaskMapper taskMapper,
                              DailyPlanMapper dailyPlanMapper,
                              DailyPlanItemMapper dailyPlanItemMapper,
                              CheckinRecordRepository checkinRecordRepository,
                              OperationLogService operationLogService) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.dailyPlanItemMapper = dailyPlanItemMapper;
        this.checkinRecordRepository = checkinRecordRepository;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanDetail checkinPlanItem(Integer projectId, Integer planItemId, CheckinRequest request) {
        getCurrentUserProject(projectId);

        DailyPlanItem item = dailyPlanItemMapper.selectById(planItemId);
        if (item == null) {
            throw new IllegalArgumentException("DailyPlanItem 不存在");
        }

        DailyPlan plan = dailyPlanMapper.selectById(item.getPlanId());
        if (plan == null || !projectId.equals(plan.getProjectId())) {
            throw new IllegalArgumentException("DailyPlanItem 不属于当前 Project");
        }

        CheckinRequest safeRequest = request == null ? new CheckinRequest() : request;
        int completedMinutes = resolveCompletedMinutes(safeRequest, item);
        LocalDate checkinDate = safeRequest.getCheckinDate() == null ? LocalDate.now() : safeRequest.getCheckinDate();
        validateCheckinDate(checkinDate);

        CheckinRecord record = checkinRecordRepository.findByPlanItemId(item.getId())
                .orElseGet(CheckinRecord::new);
        record.setPlanItemId(item.getId());
        record.setTaskId(item.getTaskId());
        record.setCompletedMinutes(completedMinutes);
        record.setCheckinDate(checkinDate);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        record.setNote(normalizeNote(safeRequest.getNote()));
        checkinRecordRepository.save(record);

        item.setActualMinutes(completedMinutes);
        item.setStatus(resolveItemStatus(item));
        dailyPlanItemMapper.updateById(item);

        PlanDetail detail = refreshDailyPlan(plan);
        operationLogService.log("CHECKIN", "CHECKIN", "Quick Check-in PlanItem " + item.getId());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanDetail deleteCheckin(Integer projectId, Integer planItemId) {
        getCurrentUserProject(projectId);

        DailyPlanItem item = dailyPlanItemMapper.selectById(planItemId);
        if (item == null) {
            throw new IllegalArgumentException("DailyPlanItem 不存在");
        }

        DailyPlan plan = dailyPlanMapper.selectById(item.getPlanId());
        if (plan == null || !projectId.equals(plan.getProjectId())) {
            throw new IllegalArgumentException("DailyPlanItem 不属于当前 Project");
        }

        CheckinRecord record = checkinRecordRepository.findByPlanItemId(item.getId())
                .orElseThrow(() -> new IllegalArgumentException("当前计划项还没有打卡记录"));
        checkinRecordRepository.delete(record);

        item.setActualMinutes(0);
        item.setStatus(STATUS_NOT_DONE);
        dailyPlanItemMapper.updateById(item);

        PlanDetail detail = refreshDailyPlan(plan);
        operationLogService.log("CHECKIN", "DELETE", "Delete Check-in PlanItem " + item.getId());
        return detail;
    }

    @Override
    public List<CheckinRecord> listProjectCheckins(Integer projectId) {
        getCurrentUserProject(projectId);
        return checkinRecordRepository.findByProjectForUser(AppUserContext.getUserId(), projectId);
    }

    @Override
    public List<CheckinRecord> listTaskCheckins(Integer projectId, Integer taskId) {
        getCurrentUserProject(projectId);
        validateTaskInProject(taskId, projectId);
        return checkinRecordRepository.findByTaskForUser(AppUserContext.getUserId(), projectId, taskId);
    }

    private Project getCurrentUserProject(Integer projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id 不能为空");
        }
        Project project = projectMapper.selectByIdAndUserId(projectId, AppUserContext.getUserId());
        if (project == null) {
            throw new IllegalArgumentException("Project 不存在或不属于当前用户");
        }
        return project;
    }

    private int resolveCompletedMinutes(CheckinRequest request, DailyPlanItem item) {
        Integer completedMinutes = request.getCompletedMinutes();
        if (completedMinutes == null) {
            completedMinutes = item.getRecommendedMinutes();
        }
        if (completedMinutes == null) {
            completedMinutes = 0;
        }
        if (completedMinutes < 0) {
            throw new IllegalArgumentException("completedMinutes 不能小于 0");
        }
        return completedMinutes;
    }

    private void validateCheckinDate(LocalDate checkinDate) {
        if (checkinDate == null) {
            throw new IllegalArgumentException("checkinDate 不能为空");
        }
        if (checkinDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("不能为未来日期打卡");
        }
    }

    private void validateTaskInProject(Integer taskId, Integer projectId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id 不能为空");
        }
        Task task = taskMapper.selectByIdAndProjectId(taskId, projectId);
        if (task == null) {
            throw new IllegalArgumentException("Task 不存在或不属于当前 Project");
        }
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private String resolveItemStatus(DailyPlanItem item) {
        int actualMinutes = item.getActualMinutes() == null ? 0 : item.getActualMinutes();
        int recommendedMinutes = item.getRecommendedMinutes() == null ? 0 : item.getRecommendedMinutes();

        if (actualMinutes <= 0) {
            return STATUS_NOT_DONE;
        }
        if (recommendedMinutes <= 0 || actualMinutes >= recommendedMinutes) {
            return STATUS_FULL_DONE;
        }
        return STATUS_PARTIAL_DONE;
    }

    private PlanDetail refreshDailyPlan(DailyPlan plan) {
        List<DailyPlanItem> items = dailyPlanItemMapper.selectListByPlanId(plan.getId());

        int totalActualMinutes = items.stream()
                .mapToInt(item -> item.getActualMinutes() == null ? 0 : item.getActualMinutes())
                .sum();

        plan.setTotalActualMinutes(totalActualMinutes);
        plan.setStatus(resolvePlanStatus(items));
        dailyPlanMapper.updateById(plan);

        attachCheckinRecords(items);
        return new PlanDetail(plan, items);
    }

    private void attachCheckinRecords(List<DailyPlanItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Integer> planItemIds = items.stream()
                .map(DailyPlanItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (planItemIds.isEmpty()) {
            return;
        }

        Map<Integer, CheckinRecord> recordMap = checkinRecordRepository.findByPlanItemIdIn(planItemIds)
                .stream()
                .collect(Collectors.toMap(CheckinRecord::getPlanItemId, record -> record));
        items.forEach(item -> item.setCheckinRecord(recordMap.get(item.getId())));
    }

    private String resolvePlanStatus(List<DailyPlanItem> items) {
        if (items.isEmpty()) {
            return STATUS_NOT_DONE;
        }

        boolean anyActual = items.stream()
                .anyMatch(item -> item.getActualMinutes() != null && item.getActualMinutes() > 0);
        if (!anyActual) {
            return STATUS_NOT_DONE;
        }

        boolean allDone = items.stream()
                .allMatch(item -> STATUS_FULL_DONE.equals(item.getStatus())
                        || (item.getRecommendedMinutes() != null
                        && item.getActualMinutes() != null
                        && item.getActualMinutes() >= item.getRecommendedMinutes()));

        return allDone ? STATUS_FULL_DONE : STATUS_PARTIAL_DONE;
    }
}
