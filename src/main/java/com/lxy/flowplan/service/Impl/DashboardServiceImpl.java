package com.lxy.flowplan.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.DailyPlanItemMapper;
import com.lxy.flowplan.mapper.DailyPlanMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.mapper.TaskMapper;
import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.DailyPlanItem;
import com.lxy.flowplan.pojo.DashboardTodayVO;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.repository.CheckinRecordRepository;
import com.lxy.flowplan.service.DashboardService;
import com.lxy.flowplan.service.ai.AiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {
    private static final String STATUS_FULL_DONE = "FULL_DONE";
    private static final int RECENT_RANGE_DAYS = 7;

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final DailyPlanMapper dailyPlanMapper;
    private final DailyPlanItemMapper dailyPlanItemMapper;
    private final CheckinRecordRepository checkinRecordRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public DashboardServiceImpl(ProjectMapper projectMapper,
                                TaskMapper taskMapper,
                                DailyPlanMapper dailyPlanMapper,
                                DailyPlanItemMapper dailyPlanItemMapper,
                                CheckinRecordRepository checkinRecordRepository,
                                AiClient aiClient,
                                ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.dailyPlanMapper = dailyPlanMapper;
        this.dailyPlanItemMapper = dailyPlanItemMapper;
        this.checkinRecordRepository = checkinRecordRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DashboardTodayVO getTodayDashboard() {
        Integer userId = AppUserContext.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        LocalDate today = LocalDate.now();
        List<Project> projects = selectUserProjects(userId);
        List<Project> activeProjects = projects.stream()
                .filter(project -> !"DONE".equals(resolveProjectStatus(project, today)))
                .toList();
        List<Integer> activeProjectIds = activeProjects.stream().map(Project::getId).toList();

        List<Task> activeTasks = selectTasks(activeProjectIds);
        Map<Integer, Task> taskMap = activeTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity(), (left, right) -> left));
        Map<Integer, List<Task>> tasksByProjectId = activeTasks.stream()
                .collect(Collectors.groupingBy(Task::getProjectId));

        List<DailyPlan> allPlans = selectPlans(activeProjectIds, null, null);
        List<DailyPlanItem> allItems = selectItems(allPlans);
        Map<Integer, List<DailyPlan>> plansByProjectId = allPlans.stream()
                .collect(Collectors.groupingBy(DailyPlan::getProjectId));
        Map<Integer, List<DailyPlanItem>> itemsByProjectId = groupItemsByProjectId(allItems, allPlans);

        List<DailyPlan> todayDailyPlans = allPlans.stream()
                .filter(plan -> today.equals(plan.getPlanDate()))
                .toList();
        List<DailyPlanItem> todayItems = selectItems(todayDailyPlans);

        List<DashboardTodayVO.DashboardTodayPlan> todayPlans = buildTodayPlans(
                activeProjects,
                todayDailyPlans,
                todayItems,
                taskMap
        );

        DashboardTodayVO.DashboardSummary summary = buildSummary(activeProjects.size(), todayPlans);
        DashboardTodayVO.DashboardRecentStats recentStats = buildRecentStats(activeProjectIds, today);
        List<DashboardTodayVO.DashboardActiveProject> activeProjectViews = buildActiveProjects(
                activeProjects,
                today,
                plansByProjectId,
                itemsByProjectId,
                tasksByProjectId
        );

        return new DashboardTodayVO(
                today,
                today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                buildGreeting(),
                summary,
                activeProjectViews,
                todayPlans,
                buildAiSuggestion(todayPlans, summary, recentStats),
                recentStats
        );
    }

    private List<Project> selectUserProjects(Integer userId) {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .orderByAsc(Project::getDeadline)
                .orderByDesc(Project::getCreatedAt)
                .orderByAsc(Project::getId));
    }

    private List<Task> selectTasks(List<Integer> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getProjectId, projectIds)
                .orderByAsc(Task::getDeadline)
                .orderByAsc(Task::getId));
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
        List<Integer> planIds = plans.stream()
                .map(DailyPlan::getId)
                .filter(Objects::nonNull)
                .toList();
        return dailyPlanItemMapper.selectListByPlanIds(planIds);
    }

    private Map<Integer, List<DailyPlanItem>> groupItemsByProjectId(List<DailyPlanItem> items,
                                                                    List<DailyPlan> plans) {
        Map<Integer, Integer> projectIdByPlanId = plans.stream()
                .collect(Collectors.toMap(DailyPlan::getId, DailyPlan::getProjectId, (left, right) -> left));
        Map<Integer, List<DailyPlanItem>> grouped = new HashMap<>();
        for (DailyPlanItem item : items) {
            Integer projectId = projectIdByPlanId.get(item.getPlanId());
            if (projectId != null) {
                grouped.computeIfAbsent(projectId, ignored -> new java.util.ArrayList<>()).add(item);
            }
        }
        return grouped;
    }

    private List<DashboardTodayVO.DashboardTodayPlan> buildTodayPlans(List<Project> activeProjects,
                                                                      List<DailyPlan> todayDailyPlans,
                                                                      List<DailyPlanItem> todayItems,
                                                                      Map<Integer, Task> taskMap) {
        Map<Integer, Project> projectMap = activeProjects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
        Map<Integer, List<DailyPlanItem>> itemMap = todayItems.stream()
                .collect(Collectors.groupingBy(DailyPlanItem::getPlanId, LinkedHashMap::new, Collectors.toList()));
        Map<Integer, Integer> projectOrder = new HashMap<>();
        for (int i = 0; i < activeProjects.size(); i++) {
            projectOrder.put(activeProjects.get(i).getId(), i);
        }

        return todayDailyPlans.stream()
                .filter(plan -> projectMap.containsKey(plan.getProjectId()))
                .sorted(Comparator.comparingInt(plan -> projectOrder.getOrDefault(plan.getProjectId(), Integer.MAX_VALUE)))
                .map(plan -> buildTodayPlan(plan, projectMap.get(plan.getProjectId()), itemMap.getOrDefault(plan.getId(), List.of()), taskMap))
                .toList();
    }

    private DashboardTodayVO.DashboardTodayPlan buildTodayPlan(DailyPlan plan,
                                                               Project project,
                                                               List<DailyPlanItem> items,
                                                               Map<Integer, Task> taskMap) {
        List<DashboardTodayVO.DashboardTodayPlanItem> planItems = items.stream()
                .map(item -> new DashboardTodayVO.DashboardTodayPlanItem(
                        item.getId(),
                        item.getTaskId(),
                        resolveTaskName(taskMap.get(item.getTaskId())),
                        safe(item.getRecommendedMinutes()),
                        safe(item.getActualMinutes()),
                        item.getStatus()
                ))
                .toList();
        int recommendedMinutes = safe(plan.getTotalRecommendedMinutes());
        int completedMinutes = safe(plan.getTotalActualMinutes());

        return new DashboardTodayVO.DashboardTodayPlan(
                plan.getProjectId(),
                project.getName(),
                recommendedMinutes,
                completedMinutes,
                calculateRate(completedMinutes, recommendedMinutes),
                planItems
        );
    }

    private DashboardTodayVO.DashboardSummary buildSummary(Integer activeProjectCount,
                                                           List<DashboardTodayVO.DashboardTodayPlan> todayPlans) {
        int totalRecommendedMinutes = todayPlans.stream()
                .mapToInt(DashboardTodayVO.DashboardTodayPlan::getRecommendedMinutes)
                .sum();
        int totalCompletedMinutes = todayPlans.stream()
                .mapToInt(DashboardTodayVO.DashboardTodayPlan::getCompletedMinutes)
                .sum();
        int totalPlanItemCount = todayPlans.stream()
                .mapToInt(plan -> plan.getItems().size())
                .sum();
        int completedPlanItemCount = todayPlans.stream()
                .flatMap(plan -> plan.getItems().stream())
                .mapToInt(item -> STATUS_FULL_DONE.equals(item.getStatus()) ? 1 : 0)
                .sum();

        return new DashboardTodayVO.DashboardSummary(
                totalRecommendedMinutes,
                totalCompletedMinutes,
                totalPlanItemCount,
                completedPlanItemCount,
                activeProjectCount,
                resolvePressureLevel(totalCompletedMinutes, totalRecommendedMinutes)
        );
    }

    private List<DashboardTodayVO.DashboardActiveProject> buildActiveProjects(List<Project> activeProjects,
                                                                              LocalDate today,
                                                                              Map<Integer, List<DailyPlan>> plansByProjectId,
                                                                              Map<Integer, List<DailyPlanItem>> itemsByProjectId,
                                                                              Map<Integer, List<Task>> tasksByProjectId) {
        return activeProjects.stream()
                .map(project -> {
                    Integer projectId = project.getId();
                    return new DashboardTodayVO.DashboardActiveProject(
                            projectId,
                            project.getName(),
                            project.getDeadline(),
                            calculateRemainingDays(today, project.getDeadline()),
                            calculateProjectProgress(
                                    plansByProjectId.getOrDefault(projectId, List.of()),
                                    itemsByProjectId.getOrDefault(projectId, List.of()),
                                    tasksByProjectId.getOrDefault(projectId, List.of())
                            ),
                            normalizeRiskLevel(project.getRiskLevel()),
                            resolveProjectStatus(project, today),
                            Boolean.TRUE.equals(project.getNeedReplan())
                    );
                })
                .toList();
    }

    private DashboardTodayVO.DashboardRecentStats buildRecentStats(List<Integer> activeProjectIds, LocalDate today) {
        LocalDate startDate = today.minusDays(RECENT_RANGE_DAYS - 1L);
        List<DailyPlan> rangePlans = selectPlans(activeProjectIds, startDate, today);
        List<DailyPlanItem> rangeItems = selectItems(rangePlans);

        int recommendedMinutes = rangePlans.stream()
                .mapToInt(plan -> safe(plan.getTotalRecommendedMinutes()))
                .sum();
        int actualMinutes = rangePlans.stream()
                .mapToInt(plan -> safe(plan.getTotalActualMinutes()))
                .sum();
        int completedItemCount = rangeItems.stream()
                .mapToInt(item -> STATUS_FULL_DONE.equals(item.getStatus()) ? 1 : 0)
                .sum();

        return new DashboardTodayVO.DashboardRecentStats(
                RECENT_RANGE_DAYS,
                calculateRate(actualMinutes, recommendedMinutes),
                actualMinutes,
                calculateStreak(today),
                completedItemCount
        );
    }

    private DashboardTodayVO.DashboardAiSuggestion buildAiSuggestion(List<DashboardTodayVO.DashboardTodayPlan> todayPlans,
                                                                     DashboardTodayVO.DashboardSummary summary,
                                                                     DashboardTodayVO.DashboardRecentStats recentStats) {
        DashboardTodayVO.DashboardAiSuggestion fallback = buildFallbackAiSuggestion(todayPlans, summary, recentStats);
        try {
            String content = aiClient.createJson(
                    buildCoachSystemPrompt(),
                    buildCoachUserPrompt(todayPlans, summary, recentStats),
                    0.75,
                    700
            );
            return parseAiSuggestion(content, fallback);
        } catch (Exception e) {
            log.warn("Dashboard AI coach generation failed, fallback will be used. reason={}", e.getMessage(), e);
            return fallback;
        }
    }

    private DashboardTodayVO.DashboardAiSuggestion buildFallbackAiSuggestion(List<DashboardTodayVO.DashboardTodayPlan> todayPlans,
                                                                            DashboardTodayVO.DashboardSummary summary,
                                                                            DashboardTodayVO.DashboardRecentStats recentStats) {
        if (todayPlans.isEmpty()) {
            return new DashboardTodayVO.DashboardAiSuggestion(
                    "今天还没有生成计划。",
                    "先到 Plans 页面为一个进行中的 Project 生成计划，Dashboard 就会自动展示今日任务。",
                    "先把今天的第一项计划建立起来，后续执行会更清晰。"
            );
        }

        String focus = todayPlans.stream()
                .flatMap(plan -> plan.getItems().stream())
                .filter(item -> !STATUS_FULL_DONE.equals(item.getStatus()))
                .sorted(Comparator.comparing(DashboardTodayVO.DashboardTodayPlanItem::getRecommendedMinutes).reversed())
                .limit(3)
                .map(DashboardTodayVO.DashboardTodayPlanItem::getTaskName)
                .collect(Collectors.joining("、"));
        if (focus.isBlank()) {
            focus = "今天的计划项已经全部完成。";
        } else {
            focus = "今天优先关注：" + focus + "。";
        }

        String suggestion = switch (summary.getPressureLevel()) {
            case "PRESSURE" -> "先完成推荐时间最长的计划项，时间不够时把低优先级任务留到明天。";
            case "RELAXED" -> "今天压力不高，可以按 Project 顺序稳定推进，完成后再补充复盘。";
            default -> "按 Dashboard 从上到下执行即可，完成一项就立即打卡，保持数据同步。";
        };

        String motivation = recentStats.getCurrentStreak() > 0
                ? "你已经连续打卡 " + recentStats.getCurrentStreak() + " 天，今天继续保持。"
                : "从今天的一次完整打卡开始，把执行节奏建立起来。";

        return new DashboardTodayVO.DashboardAiSuggestion(focus, suggestion, motivation);
    }

    private String buildCoachSystemPrompt() {
        return """
                你是 FlowPlan 的 AI Daily Coach。
                你的任务是根据用户今天的计划、完成情况和最近 7 天数据，生成亲和、温柔、人性化的中文建议。
                语气要求：
                - 像可靠的学习伙伴，不像命令式教练。
                - 温柔、具体、不过度鸡血，不制造焦虑。
                - 承认用户已经完成的部分，再给出下一步。
                - 不要编造不存在的任务、项目、分数或日期。
                - 不要使用 Markdown，不要输出代码块。

                只返回 JSON 对象，字段必须是：
                {
                  "focus": "一到两句今天的关注重点，60 字以内",
                  "suggestion": "两句具体执行建议，110 字以内",
                  "motivation": "一到两句温柔鼓励，80 字以内"
                }
                """;
    }

    private String buildCoachUserPrompt(List<DashboardTodayVO.DashboardTodayPlan> todayPlans,
                                        DashboardTodayVO.DashboardSummary summary,
                                        DashboardTodayVO.DashboardRecentStats recentStats) {
        StringBuilder builder = new StringBuilder();
        builder.append("今天 Dashboard 数据如下：\n");
        builder.append("- 今日推荐分钟：").append(summary.getTotalRecommendedMinutes()).append('\n');
        builder.append("- 今日已完成分钟：").append(summary.getTotalCompletedMinutes()).append('\n');
        builder.append("- 今日计划项数量：").append(summary.getTotalPlanItemCount()).append('\n');
        builder.append("- 今日已完成计划项：").append(summary.getCompletedPlanItemCount()).append('\n');
        builder.append("- 进行中项目数量：").append(summary.getActiveProjectCount()).append('\n');
        builder.append("- 今日压力等级：").append(summary.getPressureLevel()).append('\n');
        builder.append("- 最近 7 天完成率：").append(recentStats.getCompletionRate()).append("%\n");
        builder.append("- 最近 7 天学习时长：").append(recentStats.getStudyMinutes()).append(" 分钟\n");
        builder.append("- 当前连续打卡：").append(recentStats.getCurrentStreak()).append(" 天\n");

        if (todayPlans.isEmpty()) {
            builder.append("\n今天还没有生成计划。请温柔提醒用户先生成今天的计划，不要责备。\n");
            return builder.toString();
        }

        builder.append("\n今日 Project 和计划项：\n");
        for (DashboardTodayVO.DashboardTodayPlan plan : todayPlans) {
            builder.append("Project: ")
                    .append(plan.getProjectName())
                    .append("，推荐 ")
                    .append(plan.getRecommendedMinutes())
                    .append(" 分钟，已完成 ")
                    .append(plan.getCompletedMinutes())
                    .append(" 分钟，进度 ")
                    .append(plan.getProgressRate())
                    .append("%\n");
            for (DashboardTodayVO.DashboardTodayPlanItem item : plan.getItems()) {
                builder.append("  - ")
                        .append(item.getTaskName())
                        .append("：推荐 ")
                        .append(item.getRecommendedMinutes())
                        .append(" 分钟，已完成 ")
                        .append(item.getActualMinutes())
                        .append(" 分钟，状态 ")
                        .append(item.getStatus())
                        .append('\n');
            }
        }

        builder.append("\n请优先关注未完成且推荐时间较长的任务；如果今天已完成较多，请语气更肯定。");
        return builder.toString();
    }

    private DashboardTodayVO.DashboardAiSuggestion parseAiSuggestion(String content,
                                                                     DashboardTodayVO.DashboardAiSuggestion fallback) throws Exception {
        JsonNode root = objectMapper.readTree(stripJsonFence(content));
        String focus = normalizeAiText(root.path("focus").asText(null), fallback.getFocus());
        String suggestion = normalizeAiText(root.path("suggestion").asText(null), fallback.getSuggestion());
        String motivation = normalizeAiText(root.path("motivation").asText(null), fallback.getMotivation());
        return new DashboardTodayVO.DashboardAiSuggestion(
                trim(focus, 110),
                trim(suggestion, 180),
                trim(motivation, 130)
        );
    }

    private String stripJsonFence(String content) {
        if (content == null) {
            return "{}";
        }
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?", "").trim();
            if (normalized.endsWith("```")) {
                normalized = normalized.substring(0, normalized.length() - 3).trim();
            }
        }
        return normalized;
    }

    private String normalizeAiText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private int calculateProjectProgress(List<DailyPlan> plans, List<DailyPlanItem> items, List<Task> tasks) {
        int recommendedMinutes = plans.stream()
                .mapToInt(plan -> safe(plan.getTotalRecommendedMinutes()))
                .sum();
        int actualMinutes = plans.stream()
                .mapToInt(plan -> safe(plan.getTotalActualMinutes()))
                .sum();
        if (recommendedMinutes > 0) {
            return calculateRate(actualMinutes, recommendedMinutes);
        }

        if (tasks.isEmpty()) {
            return 0;
        }
        long doneCount = tasks.stream()
                .filter(task -> Boolean.TRUE.equals(task.getDoneFlag()) || "DONE".equals(task.getStatus()))
                .count();
        return Math.round(doneCount * 100.0f / tasks.size());
    }

    private int calculateStreak(LocalDate today) {
        List<Date> dates = checkinRecordRepository.findDistinctCheckinDatesForAnalytics(
                AppUserContext.getUserId(),
                null,
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

    private String resolveProjectStatus(Project project, LocalDate today) {
        if ("DONE".equals(project.getStatus()) || project.getFinishDate() != null) {
            return "DONE";
        }
        LocalDate deadline = project.getDeadline();
        if (deadline != null && today.isAfter(deadline)) {
            return "DONE";
        }
        LocalDate beginDate = project.getBeginDate();
        if (beginDate == null || !today.isBefore(beginDate)) {
            return "IN_PROGRESS";
        }
        return "NOT_STARTED";
    }

    private String resolvePressureLevel(int completedMinutes, int recommendedMinutes) {
        if (recommendedMinutes <= 0 || completedMinutes >= recommendedMinutes) {
            return "RELAXED";
        }
        double completionRatio = completedMinutes * 1.0 / recommendedMinutes;
        return completionRatio < 0.5 ? "PRESSURE" : "OK";
    }

    private Integer calculateRemainingDays(LocalDate today, LocalDate deadline) {
        if (deadline == null) {
            return null;
        }
        return Math.max(0, Math.toIntExact(ChronoUnit.DAYS.between(today, deadline)));
    }

    private String buildGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) {
            return "Good Morning";
        }
        if (hour < 18) {
            return "Good Afternoon";
        }
        return "Good Evening";
    }

    private String resolveTaskName(Task task) {
        if (task == null || task.getTitle() == null || task.getTitle().isBlank()) {
            return "Untitled Task";
        }
        return task.getTitle();
    }

    private String normalizeRiskLevel(String riskLevel) {
        return riskLevel == null || riskLevel.isBlank() ? "OK" : riskLevel;
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
