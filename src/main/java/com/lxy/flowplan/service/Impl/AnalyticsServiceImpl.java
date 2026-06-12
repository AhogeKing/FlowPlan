package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.DailyPlanItemMapper;
import com.lxy.flowplan.mapper.DailyPlanMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.pojo.AnalyticsCompletionTrendPoint;
import com.lxy.flowplan.pojo.AnalyticsOverview;
import com.lxy.flowplan.pojo.AnalyticsSummary;
import com.lxy.flowplan.pojo.AnalyticsTimeTrendPoint;
import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.DailyPlanItem;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.repository.CheckinRecordRepository;
import com.lxy.flowplan.service.AnalyticsService;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {
    private static final String STATUS_FULL_DONE = "FULL_DONE";

    private final ProjectMapper projectMapper;
    private final DailyPlanMapper dailyPlanMapper;
    private final DailyPlanItemMapper dailyPlanItemMapper;
    private final CheckinRecordRepository checkinRecordRepository;

    public AnalyticsServiceImpl(ProjectMapper projectMapper,
                                DailyPlanMapper dailyPlanMapper,
                                DailyPlanItemMapper dailyPlanItemMapper,
                                CheckinRecordRepository checkinRecordRepository) {
        this.projectMapper = projectMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.dailyPlanItemMapper = dailyPlanItemMapper;
        this.checkinRecordRepository = checkinRecordRepository;
    }

    @Override
    public AnalyticsOverview getOverview(Integer projectId, String range) {
        Integer userId = AppUserContext.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        String normalizedRange = normalizeRange(range);
        int days = resolveRangeDays(normalizedRange);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        List<Project> projects = resolveScopedProjects(userId, projectId);
        List<Integer> projectIds = projects.stream().map(Project::getId).toList();

        if (projectIds.isEmpty()) {
            return emptyOverview(normalizedRange, startDate, endDate);
        }

        List<DailyPlan> allPlans = selectPlans(projectIds, null, null);
        List<DailyPlan> rangePlans = selectPlans(projectIds, startDate, endDate);
        List<DailyPlanItem> allItems = selectItems(allPlans);
        List<DailyPlanItem> rangeItems = selectItems(rangePlans);

        Map<Integer, DailyPlan> allPlanMap = allPlans.stream()
                .collect(Collectors.toMap(DailyPlan::getId, plan -> plan, (left, right) -> left));
        Map<Integer, DailyPlan> rangePlanMap = rangePlans.stream()
                .collect(Collectors.toMap(DailyPlan::getId, plan -> plan, (left, right) -> left));

        AnalyticsSummary summary = buildSummary(
                projectId,
                endDate,
                rangePlans,
                rangeItems,
                allPlans,
                allItems,
                rangePlanMap
        );

        return new AnalyticsOverview(
                normalizedRange,
                summary,
                buildCompletionTrend(startDate, endDate, rangeItems, rangePlanMap),
                buildTimeTrend(startDate, endDate, rangePlans)
        );
    }

    private List<Project> resolveScopedProjects(Integer userId, Integer projectId) {
        if (projectId != null) {
            Project project = projectMapper.selectByIdAndUserId(projectId, userId);
            if (project == null) {
                throw new IllegalArgumentException("Project 不存在或不属于当前用户");
            }
            return List.of(project);
        }

        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId));
    }

    private List<DailyPlan> selectPlans(List<Integer> projectIds, LocalDate startDate, LocalDate endDate) {
        if (projectIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<DailyPlan> wrapper = new LambdaQueryWrapper<DailyPlan>()
                .in(DailyPlan::getProjectId, projectIds)
                .orderByAsc(DailyPlan::getPlanDate)
                .orderByAsc(DailyPlan::getId);

        if (startDate != null) {
            wrapper.ge(DailyPlan::getPlanDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(DailyPlan::getPlanDate, endDate);
        }
        return dailyPlanMapper.selectList(wrapper);
    }

    private List<DailyPlanItem> selectItems(List<DailyPlan> plans) {
        List<Integer> planIds = plans.stream().map(DailyPlan::getId).toList();
        return dailyPlanItemMapper.selectListByPlanIds(planIds);
    }

    private AnalyticsSummary buildSummary(Integer projectId,
                                          LocalDate today,
                                          List<DailyPlan> rangePlans,
                                          List<DailyPlanItem> rangeItems,
                                          List<DailyPlan> allPlans,
                                          List<DailyPlanItem> allItems,
                                          Map<Integer, DailyPlan> rangePlanMap) {
        int todayActualMinutes = rangePlans.stream()
                .filter(plan -> today.equals(plan.getPlanDate()))
                .mapToInt(plan -> safe(plan.getTotalActualMinutes()))
                .sum();

        int todayCompletedItems = rangeItems.stream()
                .filter(item -> {
                    DailyPlan plan = rangePlanMap.get(item.getPlanId());
                    return plan != null
                            && today.equals(plan.getPlanDate())
                            && STATUS_FULL_DONE.equals(item.getStatus());
                })
                .mapToInt(item -> 1)
                .sum();

        int rangeRecommendedMinutes = rangePlans.stream()
                .mapToInt(plan -> safe(plan.getTotalRecommendedMinutes()))
                .sum();
        int rangeActualMinutes = rangePlans.stream()
                .mapToInt(plan -> safe(plan.getTotalActualMinutes()))
                .sum();

        int totalActualMinutes = allPlans.stream()
                .mapToInt(plan -> safe(plan.getTotalActualMinutes()))
                .sum();

        int totalCompletedItems = allItems.stream()
                .filter(item -> STATUS_FULL_DONE.equals(item.getStatus()))
                .mapToInt(item -> 1)
                .sum();

        return new AnalyticsSummary(
                todayActualMinutes,
                todayCompletedItems,
                calculateRate(rangeActualMinutes, rangeRecommendedMinutes),
                totalActualMinutes,
                totalCompletedItems,
                calculateStreak(projectId, today)
        );
    }

    private List<AnalyticsCompletionTrendPoint> buildCompletionTrend(LocalDate startDate,
                                                                     LocalDate endDate,
                                                                     List<DailyPlanItem> rangeItems,
                                                                     Map<Integer, DailyPlan> planMap) {
        Map<LocalDate, Integer> recommendedCountMap = new HashMap<>();
        Map<LocalDate, Integer> completedCountMap = new HashMap<>();

        for (DailyPlanItem item : rangeItems) {
            DailyPlan plan = planMap.get(item.getPlanId());
            if (plan == null) {
                continue;
            }

            LocalDate date = plan.getPlanDate();
            recommendedCountMap.merge(date, 1, Integer::sum);
            if (STATUS_FULL_DONE.equals(item.getStatus())) {
                completedCountMap.merge(date, 1, Integer::sum);
            }
        }

        List<AnalyticsCompletionTrendPoint> trend = new ArrayList<>();
        for (LocalDate cur = startDate; !cur.isAfter(endDate); cur = cur.plusDays(1)) {
            trend.add(new AnalyticsCompletionTrendPoint(
                    cur,
                    recommendedCountMap.getOrDefault(cur, 0),
                    completedCountMap.getOrDefault(cur, 0)
            ));
        }
        return trend;
    }

    private List<AnalyticsTimeTrendPoint> buildTimeTrend(LocalDate startDate,
                                                         LocalDate endDate,
                                                         List<DailyPlan> rangePlans) {
        Map<LocalDate, Integer> recommendedMinutesMap = new HashMap<>();
        Map<LocalDate, Integer> actualMinutesMap = new HashMap<>();

        for (DailyPlan plan : rangePlans) {
            LocalDate date = plan.getPlanDate();
            recommendedMinutesMap.merge(date, safe(plan.getTotalRecommendedMinutes()), Integer::sum);
            actualMinutesMap.merge(date, safe(plan.getTotalActualMinutes()), Integer::sum);
        }

        List<AnalyticsTimeTrendPoint> trend = new ArrayList<>();
        for (LocalDate cur = startDate; !cur.isAfter(endDate); cur = cur.plusDays(1)) {
            trend.add(new AnalyticsTimeTrendPoint(
                    cur,
                    recommendedMinutesMap.getOrDefault(cur, 0),
                    actualMinutesMap.getOrDefault(cur, 0)
            ));
        }
        return trend;
    }

    private int calculateStreak(Integer projectId, LocalDate today) {
        List<Date> dates = checkinRecordRepository.findDistinctCheckinDatesForAnalytics(
                AppUserContext.getUserId(),
                projectId,
                today
        );
        Set<LocalDate> dateSet = dates.stream()
                .map(Date::toLocalDate)
                .collect(Collectors.toSet());

        LocalDate cursor = dateSet.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dateSet.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private AnalyticsOverview emptyOverview(String range, LocalDate startDate, LocalDate endDate) {
        return new AnalyticsOverview(
                range,
                new AnalyticsSummary(0, 0, 0, 0, 0, 0),
                buildCompletionTrend(startDate, endDate, List.of(), Map.of()),
                buildTimeTrend(startDate, endDate, List.of())
        );
    }

    private String normalizeRange(String range) {
        if ("30d".equalsIgnoreCase(range)) {
            return "30d";
        }
        return "7d";
    }

    private int resolveRangeDays(String range) {
        return "30d".equals(range) ? 30 : 7;
    }

    private int calculateRate(int actual, int recommended) {
        if (recommended <= 0) {
            return 0;
        }
        return Math.min(999, Math.round(actual * 100.0f / recommended));
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
