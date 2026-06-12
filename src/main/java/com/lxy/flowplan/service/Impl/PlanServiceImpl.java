package com.lxy.flowplan.service.Impl;

import com.lxy.flowplan.context.AppUserContext;
import com.lxy.flowplan.mapper.DailyPlanItemMapper;
import com.lxy.flowplan.mapper.DailyPlanMapper;
import com.lxy.flowplan.mapper.ProjectMapper;
import com.lxy.flowplan.mapper.TaskMapper;
import com.lxy.flowplan.pojo.CheckinRecord;
import com.lxy.flowplan.pojo.DailyPlan;
import com.lxy.flowplan.pojo.DailyPlanItem;
import com.lxy.flowplan.pojo.PlanDetail;
import com.lxy.flowplan.pojo.PlanGenerateResult;
import com.lxy.flowplan.pojo.PlanSetting;
import com.lxy.flowplan.pojo.Project;
import com.lxy.flowplan.pojo.Task;
import com.lxy.flowplan.repository.CheckinRecordRepository;
import com.lxy.flowplan.service.OperationLogService;
import com.lxy.flowplan.service.PlanService;
import com.lxy.flowplan.service.PlanSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlanServiceImpl implements PlanService {
    private static final double UNSCHEDULED_PRESSURE_RATIO = 0.10;
    private static final double RELAXED_UTILIZATION_RATIO = 0.80;

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final DailyPlanMapper dailyPlanMapper;
    private final DailyPlanItemMapper dailyPlanItemMapper;
    private final PlanSettingService planSettingService;
    private final CheckinRecordRepository checkinRecordRepository;
    private final OperationLogService operationLogService;

    public PlanServiceImpl(ProjectMapper projectMapper,
                           TaskMapper taskMapper,
                           PlanSettingService planSettingService,
                           DailyPlanMapper dailyPlanMapper,
                           DailyPlanItemMapper dailyPlanItemMapper,
                           CheckinRecordRepository checkinRecordRepository,
                           OperationLogService operationLogService) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.planSettingService = planSettingService;
        this.dailyPlanMapper = dailyPlanMapper;
        this.dailyPlanItemMapper = dailyPlanItemMapper;
        this.checkinRecordRepository = checkinRecordRepository;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanGenerateResult generatePlan(Integer projectId) {
        Project project = getCurrentUserProject(projectId);
        PlanSetting setting = planSettingService.resolvePlanSetting(project);

        LocalDate startDate = resolveStartDate(project);
        LocalDate endDate = project.getDeadline();
        validatePlanWindow(startDate, endDate);

        List<Task> allTasks = taskMapper.selectListByProjectId(projectId);
        allTasks.forEach(this::refreshTaskStatusIfChanged);

        List<Task> tasks = allTasks
                .stream()
                .filter(this::canBePlanned)
                .sorted(Comparator.comparing(Task::getId))
                .toList();

        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("当前 Project 下没有可排期的 Task");
        }

        Map<LocalDate, Integer> dailyCapacityMap = buildDailyCapacityMap(setting, startDate, endDate);
        int totalCapacity = dailyCapacityMap.values().stream().mapToInt(Integer::intValue).sum();
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("排期窗口内没有可用计划容量，无法生成计划");
        }

        Map<Integer, Integer> taskBudgetMap = buildTaskBudgetMap(tasks, totalCapacity);

        // 只删除今天及之后的旧计划，历史计划作为执行记录保留。
        dailyPlanMapper.deleteByProjectIdAndPlanDateGreaterThanEqual(projectId, startDate);

        PlanBuildResult buildResult = buildDailyPlans(projectId, tasks, taskBudgetMap, dailyCapacityMap, setting);
        String riskLevel = updateProjectAfterGenerate(project, buildResult, setting);

        PlanGenerateResult result = new PlanGenerateResult(
                projectId,
                startDate,
                endDate,
                buildResult.getPlanCount(),
                buildResult.getItemCount(),
                buildResult.getUnscheduledTaskMinutes(),
                riskLevel
        );
        operationLogService.log("PLAN", "GENERATE", "Generate Plan for Project " + projectId);
        return result;
    }

    @Override
    public List<DailyPlan> listPlans(Integer projectId) {
        getCurrentUserProject(projectId);
        return dailyPlanMapper.selectListByProjectId(projectId);
    }

    // 查询某个 Project
    @Override
    public PlanDetail getPlanByDate(Integer projectId, LocalDate planDate) {
        // 校验 Project 属于当前用户
        getCurrentUserProject(projectId);
        if (planDate == null) {
            throw new IllegalArgumentException("planDate 不能为空");
        }
        // 查询该 Project 在 planDate 的 DailyPlan
        DailyPlan plan = dailyPlanMapper.selectByProjectIdAndPlanDate(projectId, planDate);
        if (plan == null) {
            throw new IllegalArgumentException("指定日期没有生成计划");
        }
        // 查询这个 DailyPlan 下所有 DailyPlanItem
        List<DailyPlanItem> items = dailyPlanItemMapper.selectListByPlanId(plan.getId());
        attachCheckinRecords(items);
        // 返回 PlanDetail(plan, items)
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
                .collect(Collectors.toMap(
                        CheckinRecord::getPlanItemId,
                        record -> record
                ));
        items.forEach(item -> item.setCheckinRecord(recordMap.get(item.getId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteAllPlanByProjectId(Integer projectId) {
        Project project = getCurrentUserProject(projectId);

        int deletedCount = dailyPlanMapper.deleteByProjectId(projectId);

        project.setNeedReplan(true);
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);

        operationLogService.log("PLAN", "DELETE", "Delete all Plan for Project " + projectId);
        return "已删除 " + deletedCount + " 条 DailyPlan， Project 已标记为需要重新生成计划";
    }

    /*
        从当前登录上下文中取出 userId
        然后查找 id = projectId 且 user_id = 当前用户 id 的 Project
     */
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

    /*
        如果 project.beginState == null || beginDate 已早于今天
            从今天开始重新排期
        否则
            从 project.beginDate 开始排
     */
    private LocalDate resolveStartDate(Project project) {
        LocalDate today = LocalDate.now();
        if (project.getBeginDate() == null || project.getBeginDate().isBefore(today)) {
            return today;
        }
        return project.getBeginDate();
    }

    /**
     * 校验 Project 的排期窗口是否合法。
     * <p>
     * startDate 由 resolveStartDate(project) 得到，表示本次重新排期的起点；
     * endDate 一般是 project.deadline，表示本次排期的终点。
     */
    private void validatePlanWindow(LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            throw new IllegalArgumentException("Project deadline 不能为空，无法生成计划");
        }

        if (startDate == null) {
            throw new IllegalArgumentException("Project startDate 不能为空，无法生成计划");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Project deadline 已经过期，无法生成后续计划");
        }
    }

    // 如果 Task 已完成，不能再排
    private boolean canBePlanned(Task task) {
        return !"DONE".equals(task.getStatus()) && !Boolean.TRUE.equals(task.getDoneFlag());
    }

    private boolean refreshTaskStatusIfChanged(Task task) {
        String oldStatus = task.getStatus();
        Boolean oldDoneFlag = task.getDoneFlag();
        refreshTaskStatus(task);

        if (Objects.equals(oldStatus, task.getStatus())
                && Objects.equals(oldDoneFlag, task.getDoneFlag())) {
            return false;
        }

        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return true;
    }

    private void refreshTaskStatus(Task task) {
        if (task == null) {
            return;
        }

        if (Boolean.TRUE.equals(task.getDoneFlag()) || "DONE".equals(task.getStatus())) {
            task.setDoneFlag(true);
            task.setStatus("DONE");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate deadline = task.getDeadline();
        if (deadline != null && today.isAfter(deadline)) {
            task.setDoneFlag(true);
            task.setStatus("DONE");
            return;
        }

        task.setDoneFlag(false);
        LocalDate beginDate = task.getBeginDate();
        if (beginDate == null || !today.isBefore(beginDate)) {
            task.setStatus("IN_PROGRESS");
            return;
        }
        task.setStatus("NOT_STARTED");
    }

    private Map<LocalDate, Integer> buildDailyCapacityMap(PlanSetting setting, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Integer> map = new LinkedHashMap<>();

        for (LocalDate cur = startDate; !cur.isAfter(endDate); cur = cur.plusDays(1)) {
            int capacity = resolveDailyCapacity(setting, cur);
            map.put(cur, capacity);
        }
        return map;
    }

    /**
     * 计算某一天的可排期容量。
     * <p>
     * V1.1 中，计划时间采用离散时间块，而不是精确到 1 分钟。
     * 例如 timeBlockMinutes = 10，则每日容量尽量变成 20 / 30 / 40 / 50 ... 这种整数块。
     */
    private int resolveDailyCapacity(PlanSetting setting, LocalDate cur) {
        int ratio = resolveWeekdayRatio(setting, cur);
        int rawMinutes = setting.getBaseDailyMinutes() * ratio / 100;

        if (rawMinutes < setting.getDailyMinMinutes()) {
            return 0;
        }

        int cappedMinutes = Math.min(rawMinutes, setting.getDailyMaxMinutes());
        int roundedMinutes = roundDownToBlock(cappedMinutes, setting.getTimeBlockMinutes());

        if (roundedMinutes < setting.getDailyMinMinutes()) {
            return 0;
        }
        return roundedMinutes;
    }

    private int roundDownToBlock(int minutes, Integer blockMinutes) {
        int block = normalizeTimeBlock(blockMinutes);
        return minutes / block * block;
    }

    private int roundUpToBlock(int minutes, Integer blockMinutes) {
        int block = normalizeTimeBlock(blockMinutes);
        return (minutes + block - 1) / block * block;
    }

    private int resolveEffectiveMinSession(Task task, PlanSetting setting) {
        int minSession = task.getMinSessionMinutes() != null ? task.getMinSessionMinutes() : setting.getMinPlanItemMinutes();
        return roundUpToBlock(minSession, setting.getTimeBlockMinutes());
    }

    private int normalizeTimeBlock(Integer blockMinutes) {
        if (blockMinutes == null || blockMinutes <= 0) {
            return 10;
        }
        return blockMinutes;
    }

    private int resolveWeekdayRatio(PlanSetting setting, LocalDate cur) {
        return switch (cur.getDayOfWeek()) {
            case MONDAY -> setting.getMonRatio();
            case TUESDAY -> setting.getTueRatio();
            case WEDNESDAY -> setting.getWedRatio();
            case THURSDAY -> setting.getThuRatio();
            case FRIDAY -> setting.getFriRatio();
            case SATURDAY -> setting.getSatRatio();
            case SUNDAY -> setting.getSunRatio();
        };
    }

    // 防止非法 weight 破坏算法
    private int safeWeight(Integer weight) {
        if (weight == null || weight <= 0) {
            return 1;
        }
        return weight;
    }

    // 根据 Task.weight，把 Project 总时间预算 totalCapacity 分配给每个 Task
    // Map<TaskId, Integer>: taskId -> 这个 Task 在本次计划中应该分到多少分钟
    private Map<Integer, Integer> buildTaskBudgetMap(List<Task> tasks, int totalCapacity) {
        int totalWeight = tasks.stream()
                .mapToInt(task -> safeWeight(task.getWeight()))
                .sum();

        Map<Integer, Integer> budgetMap = new HashMap<>();

        for (Task task : tasks) {
            int weight = safeWeight(task.getWeight());
            int budget = totalCapacity * weight / totalWeight;
            budgetMap.put(task.getId(), budget);
        }
        return budgetMap;
    }

    /*
        把 taskBudgetMap 里的预算，按照 dailyCapacityMap 的每日容量，
        切成 DailyPlan 和 DailyPlanItem，然后保存进数据库。

        V1.2 核心变化：
        旧版是 while 贪心：选中最高分 Task 后直接尽量吃满当天容量。
        新版是 Daily Capacity Split：
            1. 先找出今天可排 Task
            2. 再决定今天排几个 Task
            3. 然后统一做两阶段容量分配
     */
    private PlanBuildResult buildDailyPlans(Integer projectId,
                                            List<Task> tasks,
                                            Map<Integer, Integer> remainingBudgetMap,
                                            Map<LocalDate, Integer> dailyCapacityMap,
                                            PlanSetting setting) {
        int planCount = 0;
        int itemCount = 0;
        int totalRecommendedMinutes = 0;
        int maxDailyRecommendedMinutes = 0;
        int totalAvailableCapacity = dailyCapacityMap.values().stream().mapToInt(Integer::intValue).sum();

        Map<Integer, Integer> totalBudgetMap = new HashMap<>(remainingBudgetMap);

        for (Map.Entry<LocalDate, Integer> entry : dailyCapacityMap.entrySet()) {
            LocalDate cur = entry.getKey();
            int dailyCapacity = entry.getValue();

            if (dailyCapacity <= 0) {
                continue;
            }

            // Step 1: 找出今天可排的 Task
            List<Task> candidates = findDailyCandidates(
                    tasks,
                    cur,
                    dailyCapacity,
                    remainingBudgetMap,
                    setting
            );

            if (candidates.isEmpty()) {
                continue;
            }

            // Step 2: 根据 balanceFactor、容量、taskMin/taskMax 决定今天排几个 Task
            int desiredTaskCount = resolveTodayTaskCount(
                    candidates,
                    dailyCapacity,
                    remainingBudgetMap,
                    setting
            );

            if (desiredTaskCount <= 0) {
                continue;
            }

            // Step 3: 选出今天真正参与分配的 Task
            List<Task> selectedTasks = selectDailyTasks(
                    candidates,
                    cur,
                    dailyCapacity,
                    desiredTaskCount,
                    remainingBudgetMap,
                    totalBudgetMap,
                    setting
            );

            if (selectedTasks.isEmpty()) {
                continue;
            }

            // Step 4: 两阶段分配今日容量
            Map<Integer, Integer> allocationMap = allocateDailyMinutes(
                    selectedTasks,
                    cur,
                    dailyCapacity,
                    remainingBudgetMap,
                    totalBudgetMap,
                    setting
            );

            if (allocationMap.isEmpty()) {
                continue;
            }

            DailyPlan dailyPlan = createDailyPlan(projectId, cur);
            planCount++;

            int sortOrder = 1;
            int dailyRecommendedMinutes = 0;

            for (Task task : selectedTasks) {
                int minutes = allocationMap.getOrDefault(task.getId(), 0);
                if (minutes <= 0) {
                    continue;
                }

                DailyPlanItem item = createDailyPlanItem(
                        dailyPlan.getId(),
                        task.getId(),
                        minutes,
                        sortOrder
                );

                dailyPlanItemMapper.insert(item);
                dailyPlan.setTotalRecommendedMinutes(dailyPlan.getTotalRecommendedMinutes() + minutes);
                dailyRecommendedMinutes += minutes;
                totalRecommendedMinutes += minutes;

                remainingBudgetMap.put(
                        task.getId(),
                        remainingBudgetMap.getOrDefault(task.getId(), 0) - minutes
                );

                sortOrder++;
                itemCount++;
            }

            maxDailyRecommendedMinutes = Math.max(maxDailyRecommendedMinutes, dailyRecommendedMinutes);
            dailyPlanMapper.updateById(dailyPlan);
        }

        // 允许小预算补足 minSession 后，remainingBudget 可能为负数。
        // 负数表示该 Task 已经略微超出目标预算，不能用来抵消其他 Task 的未排预算。
        int unscheduledMinutes = remainingBudgetMap.values()
                .stream()
                .mapToInt(value -> Math.max(value, 0))
                .sum();

        int totalBudgetMinutes = totalBudgetMap.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();

        return new PlanBuildResult(
                planCount,
                itemCount,
                totalAvailableCapacity,
                totalBudgetMinutes,
                totalRecommendedMinutes,
                unscheduledMinutes,
                maxDailyRecommendedMinutes
        );
    }

    private List<Task> findDailyCandidates(List<Task> tasks, LocalDate date, int dailyCapacity, Map<Integer, Integer> remainingBudgetMap, PlanSetting setting) {
        return tasks.stream()
                .filter(task -> isTaskAvailableOnDate(task, date))
                .filter(task -> remainingBudgetMap.getOrDefault(task.getId(), 0) > 0)
                .filter(task -> canGenerateMinSession(task, dailyCapacity, remainingBudgetMap, setting))
                .toList();
    }

    private boolean canGenerateMinSession(Task task, int dailyCapacity, Map<Integer, Integer> remainingBudgetMap, PlanSetting setting) {
        int remainingBudget = remainingBudgetMap.getOrDefault(task.getId(), 0);
        if (remainingBudget <= 0) {
            return false;
        }

        int effectiveMinSession = resolveEffectiveMinSession(task, setting);

        int upper = Math.min(dailyCapacity, setting.getMaxPlanItemMinutes());
        upper = roundDownToBlock(upper, setting.getTimeBlockMinutes());

        // 第一层判断：今天容量必须真的放得下一个最小 session
        if (upper < effectiveMinSession) {
            return false;
        }
        // 第二层：预算本身足够，可以直接生成
        if (remainingBudget >= effectiveMinSession) {
            return true;
        }
        // 第三层：预算不足一个 session，但允许小幅补足
        int maxAllowedByOverrun = remainingBudget + remainingBudget / 2;
        return effectiveMinSession <= maxAllowedByOverrun;
    }

    // 计算今天最多能放几个 Task
    private int resolveMaxPossibleTaskCount(List<Task> candidates,
                                            int dailyCapacity,
                                            PlanSetting setting) {
        List<Integer> minSessions = candidates.stream()
                .map(task -> resolveEffectiveMinSession(task, setting))
                .sorted()
                .toList();

        int count = 0;
        int used = 0;

        for (Integer minSession : minSessions) {
            if (count >= setting.getTaskMaxCountPerDay()) {
                break;
            }
            if (used + minSession > dailyCapacity) {
                break;
            }
            used += minSession;
            count++;
        }
        return count;
    }

    private double resolveBalanceRatio(PlanSetting setting) {
        Integer balanceFactor = setting.getBalanceFactor();
        if (balanceFactor == null) {
            return 0.5;
        }

        int safeBalanceFactor = Math.max(0, Math.min(100, balanceFactor));
        return safeBalanceFactor / 100.0;
    }

    private double smoothBalanceRatio(double ratio) {
        double x = Math.max(0.0, Math.min(1.0, ratio));
        return x * x * (3.0 - 2.0 * x);
    }

    private int resolveTodayTaskCount(List<Task> candidates,
                                      int dailyCapacity,
                                      Map<Integer, Integer> remainingBudgetMap,
                                      PlanSetting setting) {
        int maxPossibleCount = resolveMaxPossibleTaskCount(candidates, dailyCapacity, setting);
        if (maxPossibleCount <= 0) {
            return 0;
        }

        int minCount = Math.min(setting.getTaskMinCountPerDay(), maxPossibleCount);
        minCount = Math.max(minCount, 1);

        int maxCount = Math.min(setting.getTaskMaxCountPerDay(), maxPossibleCount);
        maxCount = Math.max(maxCount, minCount);

        double balanceRatio = resolveBalanceRatio(setting);
        double curvedBalanceRatio = smoothBalanceRatio(balanceRatio);

        int balanceDrivenCount = minCount + (int) Math.round((maxCount - minCount) * curvedBalanceRatio);
        int capacityDrivenCount = resolveCapacityDrivenTaskCount(candidates, dailyCapacity, remainingBudgetMap, setting);

        return Math.min(maxCount, Math.max(balanceDrivenCount, capacityDrivenCount));
    }

    private int resolveCapacityDrivenTaskCount(List<Task> candidates,
                                               int dailyCapacity,
                                               Map<Integer, Integer> remainingBudgetMap,
                                               PlanSetting setting) {
        List<Integer> maxAllowedMinutes = candidates.stream()
                .map(task -> resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting))
                .filter(minutes -> minutes > 0)
                .sorted(Comparator.reverseOrder())
                .toList();

        int count = 0;
        int capacity = 0;

        for (Integer minutes : maxAllowedMinutes) {
            if (count >= setting.getTaskMaxCountPerDay()) {
                break;
            }
            count++;
            capacity += minutes;
            if (capacity >= dailyCapacity) {
                break;
            }
        }

        return count;
    }

    // 选择今天的 Task
    private List<Task> selectDailyTasks(List<Task> candidates,
                                        LocalDate date,
                                        int dailyCapacity,
                                        int desiredTaskCount,
                                        Map<Integer, Integer> remainingBudgetMap,
                                        Map<Integer, Integer> totalBudgetMap,
                                        PlanSetting setting) {
        List<Task> sortedTasks = candidates.stream()
                .sorted(Comparator.
                        comparingDouble((Task task) -> calculateTaskScore(task, date, remainingBudgetMap, totalBudgetMap))
                        .reversed()
                        .thenComparing(Task::getId))
                .toList();

        List<Task> selectedTasks = new ArrayList<>();
        int usedCapacity = 0;

        for (Task task : sortedTasks) {
            if (selectedTasks.size() >= desiredTaskCount) {
                break;
            }

            int minSession = resolveEffectiveMinSession(task, setting);
            if (usedCapacity + minSession > dailyCapacity) {
                continue;
            }

            selectedTasks.add(task);
            usedCapacity += minSession;
        }
        return selectedTasks;
    }

    // 计算单个 item 最大允许分钟数
    // 避免二次分配无限增大某个 Task
    private int resolveMaxAllowedItemMinutes(Task task, Map<Integer, Integer> remainingBudgetMap, PlanSetting setting) {
        int remainingBudget = remainingBudgetMap.getOrDefault(task.getId(), 0);
        if (remainingBudget <= 0) {
            return 0;
        }

        int effectiveMinSession = resolveEffectiveMinSession(task, setting);
        int maxPlanItemMinutes = roundDownToBlock(setting.getMaxPlanItemMinutes(), setting.getTimeBlockMinutes());

        if (remainingBudget >= effectiveMinSession) {
            int maxByBudget = roundDownToBlock(remainingBudget, setting.getTimeBlockMinutes());
            return Math.min(maxPlanItemMinutes, Math.max(maxByBudget, effectiveMinSession));
        }

        int maxAllowedByOverrun = remainingBudget + remainingBudget / 2;
        if (effectiveMinSession <= maxAllowedByOverrun) {
            return effectiveMinSession;
        }
        return 0;
    }

    private Map<Integer, Double> buildNeedMap(List<Task> selectedTasks,
                                              LocalDate date,
                                              Map<Integer, Integer> allocationMap,
                                              Map<Integer, Integer> remainingBudgetMap,
                                              Map<Integer, Integer> totalBudgetMap,
                                              PlanSetting setting,
                                              double balanceRatio) {
        Map<Integer, Double> rawScoreMap = new HashMap<>();
        double maxScore = 0.0;

        for (Task task : selectedTasks) {
            int currentMinutes = allocationMap.getOrDefault(task.getId(), 0);
            int maxAllowed = resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting);

            // 已经没有二次分配空间的 Task，不参与 need 竞争。
            if (maxAllowed <= currentMinutes) {
                rawScoreMap.put(task.getId(), 0.0);
                continue;
            }

            double score = calculateTaskScore(task, date, remainingBudgetMap, totalBudgetMap);
            rawScoreMap.put(task.getId(), score);
            maxScore = Math.max(maxScore, score);
        }

        Map<Integer, Double> needMap = new HashMap<>();

        for (Task task : selectedTasks) {
            int currentMinutes = allocationMap.getOrDefault(task.getId(), 0);
            int maxAllowed = resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting);

            if (maxAllowed <= currentMinutes) {
                needMap.put(task.getId(), 0.0);
                continue;
            }

            double score = rawScoreMap.getOrDefault(task.getId(), 0.0);
            double normalizedScore = maxScore <= 0 ? 1.0 : score / maxScore;

            /*
                balanceFactor 越低：
                    need 越接近 normalizedScore，高分 Task 更容易吃到剩余容量。

                balanceFactor 越高：
                    need 越接近 1.0，多个 Task 更均匀地分配剩余容量。
             */
            double need = normalizedScore * (1.0 - balanceRatio) + balanceRatio;
            needMap.put(task.getId(), Math.max(need, 0.01));
        }

        return needMap;
    }

    // 二次分配剩余容量
    // balanceFactor 越低：越看 score，高分 Task 多吃剩余容量
    // balanceFactor 越高：越接近均匀分配
    private void allocateRemainingCapacity(List<Task> selectedTasks,
                                           LocalDate date,
                                           int remainingCapacity,
                                           Map<Integer, Integer> allocationMap,
                                           Map<Integer, Integer> remainingBudgetMap,
                                           Map<Integer, Integer> totalBudgetMap,
                                           PlanSetting setting) {
        int block = normalizeTimeBlock(setting.getTimeBlockMinutes());
        double balanceRatio = resolveBalanceRatio(setting);

        Map<Integer, Double> needMap = buildNeedMap(
                selectedTasks,
                date,
                allocationMap,
                remainingBudgetMap,
                totalBudgetMap,
                setting,
                balanceRatio
        );

        double totalNeed = needMap.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (totalNeed <= 0) {
            return;
        }

        int usedExtra = 0;

        // 第一轮：按 need 比例分配大块剩余容量。
        for (Task task : selectedTasks) {
            int taskId = task.getId();
            int currentMinutes = allocationMap.getOrDefault(taskId, 0);
            int maxAllowed = resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting);
            int room = maxAllowed - currentMinutes;
            int minimumAssignable = currentMinutes > 0 ? block : resolveEffectiveMinSession(task, setting);

            if (room < minimumAssignable || remainingCapacity - usedExtra < minimumAssignable) {
                continue;
            }

            double need = needMap.getOrDefault(taskId, 0.0);
            int rawExtra = (int) Math.floor(remainingCapacity * need / totalNeed);
            int extra = roundDownToBlock(rawExtra, setting.getTimeBlockMinutes());

            extra = Math.min(extra, room);
            extra = Math.min(extra, remainingCapacity - usedExtra);
            extra = roundDownToBlock(extra, setting.getTimeBlockMinutes());

            if (extra < minimumAssignable) {
                continue;
            }

            allocationMap.put(taskId, currentMinutes + extra);
            usedExtra += extra;
        }

        int leftover = remainingCapacity - usedExtra;
        leftover = roundDownToBlock(leftover, setting.getTimeBlockMinutes());

        // 第二轮：处理第一轮按比例取整后残留的 block。
        while (leftover >= block) {
            int availableLeftover = leftover;
            Optional<Task> optionalTask = selectedTasks.stream()
                    .filter(task -> {
                        int currentMinutes = allocationMap.getOrDefault(task.getId(), 0);
                        int maxAllowed = resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting);
                        int required = currentMinutes > 0 ? block : resolveEffectiveMinSession(task, setting);
                        return availableLeftover >= required && maxAllowed - currentMinutes >= required;
                    })
                    .max(Comparator
                            .comparingDouble((Task task) -> needMap.getOrDefault(task.getId(), 0.0))
                            .thenComparing(Task::getId));

            if (optionalTask.isEmpty()) {
                break;
            }

            Task task = optionalTask.get();
            int currentMinutes = allocationMap.getOrDefault(task.getId(), 0);
            int extra = currentMinutes > 0 ? block : resolveEffectiveMinSession(task, setting);
            allocationMap.put(task.getId(), currentMinutes + extra);
            leftover -= extra;
        }
    }

    private int resolveStageOneCapacity(int dailyCapacity, PlanSetting setting) {
        int rawCapacity = (int) Math.floor(dailyCapacity * resolveBalanceRatio(setting));
        int roundedCapacity = roundDownToBlock(rawCapacity, setting.getTimeBlockMinutes());
        return Math.min(dailyCapacity, roundedCapacity);
    }

    // 两阶段分配今日容量
    private Map<Integer, Integer> allocateDailyMinutes(List<Task> selectedTasks,
                                                       LocalDate date,
                                                       int dailyCapacity,
                                                       Map<Integer, Integer> remainingBudgetMap,
                                                       Map<Integer, Integer> totalBudgetMap,
                                                       PlanSetting setting) {
        Map<Integer, Integer> allocationMap = new LinkedHashMap<>();

        int usedCapacity = 0;
        int reserveCapacity = resolveStageOneCapacity(dailyCapacity, setting);

        // Stage 1: Min Session reservation. balanceFactor 越高，越愿意先给多个 Task 保底 session。
        for (Task task : selectedTasks) {
            int minSession = resolveEffectiveMinSession(task, setting);
            int maxAllowed = resolveMaxAllowedItemMinutes(task, remainingBudgetMap, setting);

            if (maxAllowed < minSession) {
                continue;
            }
            if (usedCapacity + minSession > reserveCapacity) {
                continue;
            }

            allocationMap.put(task.getId(), minSession);
            usedCapacity += minSession;
        }

        int remainingCapacity = dailyCapacity - usedCapacity;
        remainingCapacity = roundDownToBlock(remainingCapacity, setting.getTimeBlockMinutes());

        if (remainingCapacity <= 0) {
            return allocationMap;
        }

        // Stage 2: Secondary Allocation
        allocateRemainingCapacity(
                selectedTasks,
                date,
                remainingCapacity,
                allocationMap,
                remainingBudgetMap,
                totalBudgetMap,
                setting
        );
        return allocationMap;
    }

    private double calculateTaskScore(Task task,
                                      LocalDate date,
                                      Map<Integer, Integer> remainingBudgetMap,
                                      Map<Integer, Integer> totalBudgetMap) {
        int remainingBudget = remainingBudgetMap.getOrDefault(task.getId(), 0);
        int totalBudget = totalBudgetMap.getOrDefault(task.getId(), 1);

        double remainingBudgetRatio = remainingBudget * 1.0 / Math.max(totalBudget, 1);

        long daysUntilDeadline = resolveDaysUntilDeadline(task, date);
        double deadlineUrgency = 1.0 / (daysUntilDeadline + 1);

        return remainingBudgetRatio * 100.0 + deadlineUrgency * 30.0;
    }

    private long resolveDaysUntilDeadline(Task task, LocalDate date) {
        if (task.getDeadline() == null) {
            return 9999;
        }
        long days = ChronoUnit.DAYS.between(date, task.getDeadline());
        return Math.max(days, 0);
    }

    private boolean isTaskAvailableOnDate(Task task, LocalDate planDate) {
        // Task 还没到开始日期，暂时不排
        if (task.getBeginDate() != null && task.getBeginDate().isAfter(planDate)) {
            return false;
        }
        // Task 已经过了自己的 deadline，不能排
        return task.getDeadline() == null || !task.getDeadline().isBefore(planDate);
    }

    /*
        创建 DailyPlan，并立即保存到数据库
     */
    private DailyPlan createDailyPlan(Integer projectId, LocalDate planDate) {
        DailyPlan plan = new DailyPlan();
        plan.setProjectId(projectId);
        plan.setPlanDate(planDate);
        plan.setTotalRecommendedMinutes(0);
        plan.setTotalActualMinutes(0);
        plan.setStatus("NOT_DONE");

        dailyPlanMapper.insert(plan);
        return plan;
    }

    private DailyPlanItem createDailyPlanItem(Integer planId, Integer taskId, Integer recommendedMinutes, Integer sortOrder) {
        DailyPlanItem item = new DailyPlanItem();
        item.setPlanId(planId);
        item.setTaskId(taskId);
        item.setRecommendedMinutes(recommendedMinutes);
        item.setActualMinutes(0);
        item.setSortOrder(sortOrder);
        item.setStatus("NOT_DONE");
        item.setReason("基于权重预算、任务评分、balanceFactor 和两阶段容量拆分自动生成");

        return item;
    }

    private String updateProjectAfterGenerate(Project project, PlanBuildResult result, PlanSetting setting) {
        String riskLevel = resolveSchedulePressure(result, setting);

        project.setNeedReplan(false);
        project.setRiskLevel(riskLevel);
        project.setUpdatedAt(LocalDateTime.now());

        projectMapper.updateById(project);
        return riskLevel;
    }

    private String resolveSchedulePressure(PlanBuildResult result, PlanSetting setting) {
        if (result.getTotalBudgetMinutes() <= 0) {
            return "RELAXED";
        }

        if (result.getPlanCount() == 0 || result.getItemCount() == 0) {
            return "PRESSURE";
        }

        if (result.getTotalRecommendedMinutes() > result.getTotalAvailableCapacity()) {
            return "PRESSURE";
        }

        double unscheduledRatio = result.getUnscheduledTaskMinutes() * 1.0
                / Math.max(result.getTotalBudgetMinutes(), 1);
        if (unscheduledRatio >= UNSCHEDULED_PRESSURE_RATIO) {
            return "PRESSURE";
        }

        double utilizationRatio = result.getTotalRecommendedMinutes() * 1.0
                / Math.max(result.getTotalAvailableCapacity(), 1);
        if (utilizationRatio <= RELAXED_UTILIZATION_RATIO) {
            return "RELAXED";
        }

        double activeAvgDailyRecommendedMinutes = result.getTotalRecommendedMinutes() * 1.0
                / Math.max(result.getPlanCount(), 1);
        if (activeAvgDailyRecommendedMinutes < setting.getDailyMinMinutes()) {
            return "RELAXED";
        }

        if (result.getMaxDailyRecommendedMinutes() > setting.getDailyMaxMinutes()) {
            return "PRESSURE";
        }

        return "OK";
    }

    @lombok.Getter
    @lombok.AllArgsConstructor
    private static class PlanBuildResult {
        private final int planCount;
        private final int itemCount;
        private final int totalAvailableCapacity;
        private final int totalBudgetMinutes;
        private final int totalRecommendedMinutes;
        private final int unscheduledTaskMinutes;
        private final int maxDailyRecommendedMinutes;
    }
}
