package com.lxy.flowplan.service.ai;

import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.dto.ai.DomainType;
import com.lxy.flowplan.dto.ai.PlanSettingDraft;
import com.lxy.flowplan.dto.ai.ProjectDraft;
import com.lxy.flowplan.dto.ai.TaskDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiTemplateService {
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern CHINESE_DATE_PATTERN = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern HOUR_RANGE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-|~|到|至)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:个)?\\s*(?:小时|h)");
    private static final Pattern HOUR_SINGLE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:个)?\\s*(?:小时|h)");
    private static final Pattern CHINESE_HOUR_RANGE_PATTERN = Pattern.compile("([半一二两三四五六七八九十]+)\\s*(?:-|~|到|至)\\s*([半一二两三四五六七八九十]+)\\s*(?:个)?\\s*小时");
    private static final Pattern CHINESE_HOUR_SINGLE_PATTERN = Pattern.compile("([半一二两三四五六七八九十]+)\\s*(?:个)?\\s*小时");
    private static final Pattern DAY_AVAILABILITY_PATTERN = Pattern.compile("(周[一二三四五六日天](?:\\s*(?:到|至|-|~)\\s*周?[一二三四五六日天])?(?:\\s*(?:、|和|及|,|，)?\\s*周?[一二三四五六日天])*)[^。；;]{0,40}?((?:\\d+(?:\\.\\d+)?|[半一二两三四五六七八九十]+)\\s*(?:(?:-|~|到|至)\\s*(?:\\d+(?:\\.\\d+)?|[半一二两三四五六七八九十]+))?\\s*(?:个)?\\s*(?:小时|h))");
    private static final Pattern HOUR_AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?|[半一二两三四五六七八九十]+)\\s*(?:(?:-|~|到|至)\\s*(\\d+(?:\\.\\d+)?|[半一二两三四五六七八九十]+))?");

    public DomainType detectDomain(String message) {
        String text = normalize(message);
        if (containsAny(text, "雅思", "托福", "四六级", "六级", "四级", "英语", "口语", "听力", "阅读", "写作")) {
            return DomainType.ENGLISH_LEARNING;
        }
        if (containsAny(text, "c++", "java", "python", "go", "javascript", "spring", "vue", "算法", "编程", "后端", "前端")) {
            return DomainType.PROGRAMMING_LANGUAGE;
        }
        if (containsAny(text, "考研", "408", "政治", "专业课", "数学一", "数学二", "数学三")) {
            return DomainType.CHINESE_POSTGRAD_EXAM;
        }
        return DomainType.GENERAL;
    }

    public String buildTemplatePrompt(DomainType domainType) {
        return switch (domainType) {
            case ENGLISH_LEARNING -> """
                    英语学习规划要点：
                    - 听力训练：weight 4, minSessionMinutes 30
                    - 阅读训练：weight 4, minSessionMinutes 30
                    - 写作训练：weight 5, minSessionMinutes 40
                    - 口语训练：weight 3, minSessionMinutes 30
                    - 词汇复习：weight 3, minSessionMinutes 20
                    - 模考与复盘：weight 4, minSessionMinutes 60
                    如果用户说某项薄弱，对应 weight 可提高到 5。
                    """;
            case PROGRAMMING_LANGUAGE -> """
                    编程学习规划要点：
                    - 基础语法：weight 3, minSessionMinutes 30
                    - 核心概念：weight 4, minSessionMinutes 40
                    - 标准库与常用 API：weight 4, minSessionMinutes 40
                    - 练习题与小练习：weight 3, minSessionMinutes 30
                    - 项目实践：weight 5, minSessionMinutes 60
                    - 调试复盘与笔记：weight 3, minSessionMinutes 30
                    如果用户偏项目开发，提高项目实践权重；如果偏面试，提高核心概念和练习题权重。
                    """;
            case CHINESE_POSTGRAD_EXAM -> """
                    中国考研规划要点：
                    - 数学复习：weight 5, minSessionMinutes 60
                    - 英语阅读与词汇：weight 4, minSessionMinutes 40
                    - 政治知识点与选择题：weight 3, minSessionMinutes 30
                    - 专业课复习：weight 5, minSessionMinutes 60
                    - 模考与错题复盘：weight 4, minSessionMinutes 60
                    - 周总结与计划调整：weight 2, minSessionMinutes 30
                    如果用户说数学或专业课重要，对应 weight 保持 5。
                    """;
            case GENERAL -> """
                    通用目标规划要点：
                    - 目标拆解与资料整理：weight 3, minSessionMinutes 30
                    - 核心学习与输入：weight 4, minSessionMinutes 40
                    - 实践输出：weight 5, minSessionMinutes 60
                    - 反馈修正：weight 3, minSessionMinutes 30
                    - 阶段复盘：weight 2, minSessionMinutes 30
                    请结合用户目标替换为更具体的中文任务名，但不要超过 8 个任务。
                    """;
        };
    }

    public AiProjectDraft buildFallbackDraft(String message, DomainType domainType, LocalDate today) {
        LocalDate beginDate = today;
        LocalDate deadline = resolveDeadline(message, today);

        AiProjectDraft draft = new AiProjectDraft();
        ProjectDraft project = new ProjectDraft();
        project.setName(resolveProjectName(message, domainType));
        project.setDescription(resolveProjectDescription(message, domainType, deadline));
        project.setBeginDate(beginDate);
        project.setDeadline(deadline);
        draft.setProject(project);
        draft.setTasks(buildTasks(domainType, message, beginDate, deadline));
        draft.setSetting(buildSetting(message, domainType));
        draft.setExplanation("已根据你的目标、可投入时间和重点方向生成项目草案，确认后会为你生成每日计划。");
        draft.setDomainType(domainType);

        List<String> warnings = new ArrayList<>();
        if (domainType == DomainType.GENERAL) {
            warnings.add("目标类型较开放，已按通用学习目标进行拆解。");
        }
        if (!hasTimeSignal(message)) {
            warnings.add("未提供明确周期，已默认生成 30 天计划。");
        }
        draft.setWarnings(warnings);
        return draft;
    }

    public void alignSettingWithUserAvailability(AiProjectDraft draft, String message) {
        DailyAvailability availability = resolveDailyAvailability(message);
        if (draft == null || availability == null) {
            return;
        }

        PlanSettingDraft setting = draft.getSetting();
        if (setting == null) {
            setting = new PlanSettingDraft();
            draft.setSetting(setting);
        }

        boolean adjusted = false;
        if (setting.getBaseDailyMinutes() == null || setting.getBaseDailyMinutes() < availability.baseMinutes) {
            setting.setBaseDailyMinutes(availability.baseMinutes);
            adjusted = true;
        }
        if (setting.getDailyMaxMinutes() == null || setting.getDailyMaxMinutes() < availability.maxMinutes) {
            setting.setDailyMaxMinutes(availability.maxMinutes);
            adjusted = true;
        }

        if (availability.baseMinutes >= 180) {
            if (setting.getTaskMaxCountPerDay() == null || setting.getTaskMaxCountPerDay() < 4) {
                setting.setTaskMaxCountPerDay(4);
                adjusted = true;
            }
            if (setting.getMaxPlanItemMinutes() == null || setting.getMaxPlanItemMinutes() < 120) {
                setting.setMaxPlanItemMinutes(120);
                adjusted = true;
            }
        }
        if (availability.baseMinutes >= 240) {
            if (setting.getTaskMaxCountPerDay() == null || setting.getTaskMaxCountPerDay() < 5) {
                setting.setTaskMaxCountPerDay(5);
                adjusted = true;
            }
            if (setting.getMaxPlanItemMinutes() == null || setting.getMaxPlanItemMinutes() < 150) {
                setting.setMaxPlanItemMinutes(150);
                adjusted = true;
            }
        }

        if (adjusted) {
            List<String> warnings = draft.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(draft.getWarnings());
            warnings.add("已按你提供的每日可投入时间调整计划容量。");
            draft.setWarnings(warnings);
        }
    }

    public String buildAvailabilityPrompt(String message) {
        DailyAvailability availability = resolveDailyAvailability(message);
        if (availability == null) {
            return "未识别到明确每日可投入时长；可以使用合理默认学习节奏。";
        }

        int taskMaxCount = availability.baseMinutes >= 240 ? 5 : availability.baseMinutes >= 180 ? 4 : 3;
        int maxPlanItemMinutes = availability.baseMinutes >= 240 ? 150 : availability.baseMinutes >= 180 ? 120 : 90;
        String dayRatioPrompt = buildDayRatioPrompt(message, availability.baseMinutes);
        return """
                已识别到用户明确给出了每日可投入时间：
                - baseDailyMinutes 必须至少为 %d。
                - dailyMaxMinutes 必须至少为 %d。
                - taskMaxCountPerDay 建议至少为 %d，避免每日容量无法被任务吃满。
                - maxPlanItemMinutes 建议至少为 %d。
                %s
                - 不要在 warnings 中说这些值来自系统默认、模板或内部算法；可以直接说明已按用户可投入时间安排节奏。
                """.formatted(
                availability.baseMinutes,
                availability.maxMinutes,
                taskMaxCount,
                maxPlanItemMinutes,
                dayRatioPrompt
        );
    }

    public boolean containsExplicitDailyAvailability(String message) {
        return resolveDailyAvailability(message) != null;
    }

    private List<TaskDraft> buildTasks(DomainType domainType, String message, LocalDate beginDate, LocalDate deadline) {
        return switch (domainType) {
            case ENGLISH_LEARNING -> englishTasks(beginDate, deadline);
            case PROGRAMMING_LANGUAGE -> programmingTasks(message, beginDate, deadline);
            case CHINESE_POSTGRAD_EXAM -> examTasks(beginDate, deadline);
            case GENERAL -> generalTasks(message, beginDate, deadline);
        };
    }

    private List<TaskDraft> englishTasks(LocalDate beginDate, LocalDate deadline) {
        return List.of(
                task("听力训练", "精听练习、错题复盘和听力材料积累。", 4, 30, beginDate, deadline),
                task("阅读训练", "阅读理解、长难句分析和错题整理。", 4, 30, beginDate, deadline),
                task("写作训练", "作文结构、论证表达和批改复盘。", 5, 40, beginDate, deadline),
                task("口语训练", "口语题库练习、录音复盘和表达积累。", 3, 30, beginDate, deadline),
                task("词汇复习", "单词记忆、例句复习和高频词巩固。", 3, 20, beginDate, deadline),
                task("模考与复盘", "阶段模考、时间控制和薄弱点总结。", 4, 60, beginDate, deadline)
        );
    }

    private List<TaskDraft> programmingTasks(String message, LocalDate beginDate, LocalDate deadline) {
        String topic = extractProgrammingTopic(message);
        if ("C++".equals(topic)) {
            return List.of(
                    task("C++ 基础语法", "掌握基本语法、编译流程和常用表达。", 3, 30, beginDate, deadline),
                    task("STL 容器与算法", "学习常用容器、迭代器和标准算法。", 4, 40, beginDate, deadline),
                    task("指针、引用与内存管理", "理解指针、引用、生命周期和内存安全。", 5, 40, beginDate, deadline),
                    task("面向对象与 RAII", "学习类、继承、多态和资源管理模式。", 4, 40, beginDate, deadline),
                    task("现代 C++ 特性", "学习智能指针、移动语义、lambda 和常用现代语法。", 4, 40, beginDate, deadline),
                    task("小项目实践", "完成一个可运行的小项目并持续重构。", 5, 60, beginDate, deadline)
            );
        }
        return List.of(
                task(topic + " 基础语法", "掌握基础语法和常用表达。", 3, 30, beginDate, deadline),
                task(topic + " 核心概念", "理解语言核心机制和常见开发模式。", 4, 40, beginDate, deadline),
                task("常用 API 与工具", "学习标准库、框架和常用开发工具。", 4, 40, beginDate, deadline),
                task("练习题与小练习", "通过小练习巩固语法和思路。", 3, 30, beginDate, deadline),
                task("项目实践", "完成面向真实场景的小项目。", 5, 60, beginDate, deadline),
                task("调试复盘与笔记", "整理问题、复盘 bug 和沉淀学习笔记。", 3, 30, beginDate, deadline)
        );
    }

    private List<TaskDraft> examTasks(LocalDate beginDate, LocalDate deadline) {
        return List.of(
                task("数学复习", "基础知识、强化题型和错题复盘。", 5, 60, beginDate, deadline),
                task("英语阅读与词汇", "词汇、长难句、阅读理解和真题复盘。", 4, 40, beginDate, deadline),
                task("政治知识点与选择题", "政治知识点梳理和选择题训练。", 3, 30, beginDate, deadline),
                task("专业课复习", "专业课知识体系、重点章节和真题训练。", 5, 60, beginDate, deadline),
                task("模考与错题复盘", "模拟考试、时间控制和错题整理。", 4, 60, beginDate, deadline),
                task("周总结与计划调整", "每周复盘完成情况并调整后续节奏。", 2, 30, beginDate, deadline)
        );
    }

    private List<TaskDraft> generalTasks(String message, LocalDate beginDate, LocalDate deadline) {
        String target = resolveShortTarget(message);
        return List.of(
                task(target + "目标拆解", "明确阶段目标、资料和完成标准。", 3, 30, beginDate, deadline),
                task(target + "核心学习", "围绕核心内容进行系统输入。", 4, 40, beginDate, deadline),
                task(target + "实践输出", "通过练习、作品或项目产出检验学习效果。", 5, 60, beginDate, deadline),
                task(target + "反馈修正", "根据结果调整方法并补齐薄弱点。", 3, 30, beginDate, deadline),
                task(target + "阶段复盘", "定期总结进度、问题和下一步安排。", 2, 30, beginDate, deadline)
        );
    }

    private PlanSettingDraft buildSetting(String message, DomainType domainType) {
        PlanSettingDraft setting = new PlanSettingDraft();
        int base = 120;
        int max = 180;
        String text = normalize(message);
        DailyAvailability availability = resolveDailyAvailability(message);

        if (availability != null) {
            base = availability.baseMinutes;
            max = availability.maxMinutes;
        } else if (containsAny(text, "半小时", "30分钟", "三十分钟")) {
            base = 30;
            max = 60;
        } else if (containsAny(text, "1到2小时", "1-2小时", "1~2小时", "一到两小时", "1 到 2 小时")) {
            base = 90;
            max = 150;
        } else if (containsAny(text, "1小时", "一小时")) {
            base = 60;
            max = 90;
        } else if (containsAny(text, "2小时", "两小时", "二小时")) {
            base = 120;
            max = 180;
        } else if (containsAny(text, "3小时", "三小时", "3小时以上")) {
            base = 180;
            max = 240;
        } else if (domainType == DomainType.CHINESE_POSTGRAD_EXAM) {
            base = 180;
            max = 240;
        }

        setting.setBaseDailyMinutes(base);
        setting.setDailyMinMinutes(Math.min(20, base));
        setting.setDailyMaxMinutes(max);

        int weekdayRatio = containsAny(text, "工作日很忙", "平时很忙") ? 70 : 100;
        int weekendRatio = containsAny(text, "周末不学", "周末不学习") ? 0 : 100;
        if (containsAny(text, "周末更多", "周末多", "周末可以多")) {
            weekendRatio = 150;
        }
        if (containsAny(text, "只有周末")) {
            weekdayRatio = 0;
            weekendRatio = 200;
        }
        setting.setMonRatio(weekdayRatio);
        setting.setTueRatio(weekdayRatio);
        setting.setWedRatio(weekdayRatio);
        setting.setThuRatio(weekdayRatio);
        setting.setFriRatio(weekdayRatio);
        setting.setSatRatio(weekendRatio);
        setting.setSunRatio(weekendRatio);
        applyDayAvailabilityRatios(setting, base, message);

        setting.setTaskMinCountPerDay(containsAny(text, "多任务", "并行") ? 2 : 1);
        int taskMaxCount = containsAny(text, "专注", "很累", "时间少") ? 2 : 4;
        if (base >= 240) {
            taskMaxCount = Math.max(taskMaxCount, 5);
        }
        setting.setTaskMaxCountPerDay(taskMaxCount);
        setting.setMinPlanItemMinutes(domainType == DomainType.ENGLISH_LEARNING ? 20 : 30);
        int maxPlanItemMinutes = domainType == DomainType.ENGLISH_LEARNING ? 90 : 120;
        if (base >= 180) {
            maxPlanItemMinutes = Math.max(maxPlanItemMinutes, 120);
        }
        if (base >= 240) {
            maxPlanItemMinutes = Math.max(maxPlanItemMinutes, 150);
        }
        setting.setMaxPlanItemMinutes(maxPlanItemMinutes);
        setting.setTimeBlockMinutes(10);
        setting.setBalanceFactor(resolveBalanceFactor(text, domainType));
        return setting;
    }

    private DailyAvailability resolveDailyAvailability(String message) {
        String text = normalize(message);
        if (text.isBlank()) {
            return null;
        }

        List<DayAvailability> dayAvailabilities = resolveDayAvailabilities(text);
        if (!dayAvailabilities.isEmpty()) {
            int baseMinutes = dayAvailabilities.stream()
                    .mapToInt(DayAvailability::minutes)
                    .max()
                    .orElse(120);
            int maxMinutes = dayAvailabilities.stream()
                    .mapToInt(DayAvailability::maxMinutes)
                    .max()
                    .orElse(baseMinutes);
            return new DailyAvailability(baseMinutes, Math.max(baseMinutes, maxMinutes));
        }

        Matcher rangeMatcher = HOUR_RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            double minHours = parseDouble(rangeMatcher.group(1));
            double maxHours = parseDouble(rangeMatcher.group(2));
            return buildAvailability(minHours, maxHours);
        }

        Matcher chineseRangeMatcher = CHINESE_HOUR_RANGE_PATTERN.matcher(text);
        if (chineseRangeMatcher.find()) {
            Double minHours = parseChineseNumber(chineseRangeMatcher.group(1));
            Double maxHours = parseChineseNumber(chineseRangeMatcher.group(2));
            if (minHours != null && maxHours != null) {
                return buildAvailability(minHours, maxHours);
            }
        }

        Matcher singleMatcher = HOUR_SINGLE_PATTERN.matcher(text);
        if (singleMatcher.find()) {
            double hours = parseDouble(singleMatcher.group(1));
            return buildAvailability(hours, hours + 1.0);
        }

        Matcher chineseSingleMatcher = CHINESE_HOUR_SINGLE_PATTERN.matcher(text);
        if (chineseSingleMatcher.find()) {
            Double hours = parseChineseNumber(chineseSingleMatcher.group(1));
            if (hours != null) {
                return buildAvailability(hours, hours + 1.0);
            }
        }

        if (containsAny(text, "半小时", "三十分钟", "30分钟")) {
            return new DailyAvailability(30, 60);
        }
        return null;
    }

    private DailyAvailability buildAvailability(double minHours, double maxHours) {
        double safeMinHours = Math.max(0.5, Math.min(minHours, maxHours));
        double safeMaxHours = Math.max(safeMinHours, Math.max(minHours, maxHours));
        int baseMinutes = roundToTen((int) Math.round((safeMinHours + safeMaxHours) * 30));
        int maxMinutes = roundToTen((int) Math.round(safeMaxHours * 60));
        return new DailyAvailability(baseMinutes, Math.max(baseMinutes, maxMinutes));
    }

    private void applyDayAvailabilityRatios(PlanSettingDraft setting, int baseMinutes, String message) {
        if (baseMinutes <= 0) {
            return;
        }
        for (DayAvailability availability : resolveDayAvailabilities(normalize(message))) {
            int ratio = Math.max(0, Math.min(300, roundToFive(availability.minutes() * 100 / baseMinutes)));
            if (availability.days()[0]) {
                setting.setMonRatio(ratio);
            }
            if (availability.days()[1]) {
                setting.setTueRatio(ratio);
            }
            if (availability.days()[2]) {
                setting.setWedRatio(ratio);
            }
            if (availability.days()[3]) {
                setting.setThuRatio(ratio);
            }
            if (availability.days()[4]) {
                setting.setFriRatio(ratio);
            }
            if (availability.days()[5]) {
                setting.setSatRatio(ratio);
            }
            if (availability.days()[6]) {
                setting.setSunRatio(ratio);
            }
        }
    }

    private String buildDayRatioPrompt(String message, int baseMinutes) {
        List<DayAvailability> availabilities = resolveDayAvailabilities(normalize(message));
        if (availabilities.isEmpty() || baseMinutes <= 0) {
            return "- 如果用户没有区分日期，weekday ratio 可按整体节奏设置。";
        }

        StringBuilder builder = new StringBuilder("- 用户区分了不同日期可投入时间，weekday ratio 必须体现差异：");
        int[] ratios = new int[]{-1, -1, -1, -1, -1, -1, -1};
        for (DayAvailability availability : availabilities) {
            int ratio = Math.max(0, Math.min(300, roundToFive(availability.minutes() * 100 / baseMinutes)));
            for (int index = 0; index < availability.days().length; index++) {
                if (availability.days()[index]) {
                    ratios[index] = ratio;
                }
            }
        }

        String[] labels = {"monRatio", "tueRatio", "wedRatio", "thuRatio", "friRatio", "satRatio", "sunRatio"};
        for (int index = 0; index < ratios.length; index++) {
            if (ratios[index] >= 0) {
                builder.append(" ").append(labels[index]).append("=").append(ratios[index]).append(";");
            }
        }
        return builder.toString();
    }

    private List<DayAvailability> resolveDayAvailabilities(String normalizedMessage) {
        String text = normalizedMessage == null ? "" : normalizedMessage.replace("星期", "周");
        List<DayAvailability> result = new ArrayList<>();
        Matcher matcher = DAY_AVAILABILITY_PATTERN.matcher(text);
        while (matcher.find()) {
            boolean[] days = parseDayExpression(matcher.group(1));
            HourAvailability availability = parseHourAvailability(matcher.group(2));
            if (availability != null && hasAnyDay(days)) {
                result.add(new DayAvailability(days, availability.minutes(), availability.maxMinutes()));
            }
        }
        return result;
    }

    private boolean[] parseDayExpression(String expression) {
        boolean[] days = new boolean[7];
        String text = expression == null ? "" : expression.replace("星期", "周").replaceAll("\\s+", "");
        Matcher rangeMatcher = Pattern.compile("周([一二三四五六日天])(?:到|至|-|~)周?([一二三四五六日天])").matcher(text);
        if (rangeMatcher.find()) {
            int start = dayIndex(rangeMatcher.group(1).charAt(0));
            int end = dayIndex(rangeMatcher.group(2).charAt(0));
            if (start >= 0 && end >= 0) {
                for (int index = start; index <= end; index++) {
                    days[index] = true;
                }
            }
        }

        Matcher singleMatcher = Pattern.compile("周([一二三四五六日天])").matcher(text);
        while (singleMatcher.find()) {
            int index = dayIndex(singleMatcher.group(1).charAt(0));
            if (index >= 0) {
                days[index] = true;
            }
        }
        return days;
    }

    private int dayIndex(char value) {
        return switch (value) {
            case '一' -> 0;
            case '二' -> 1;
            case '三' -> 2;
            case '四' -> 3;
            case '五' -> 4;
            case '六' -> 5;
            case '日', '天' -> 6;
            default -> -1;
        };
    }

    private boolean hasAnyDay(boolean[] days) {
        for (boolean day : days) {
            if (day) {
                return true;
            }
        }
        return false;
    }

    private HourAvailability parseHourAvailability(String expression) {
        Matcher matcher = HOUR_AMOUNT_PATTERN.matcher(expression == null ? "" : expression);
        if (!matcher.find()) {
            return null;
        }
        Double first = parseHourValue(matcher.group(1));
        if (first == null) {
            return null;
        }
        Double second = parseHourValue(matcher.group(2));
        double hours = second == null ? first : (first + second) / 2.0;
        double maxHours = second == null ? first : Math.max(first, second);
        int minutes = roundToTen((int) Math.round(hours * 60));
        int maxMinutes = roundToTen((int) Math.round(maxHours * 60));
        return new HourAvailability(minutes, Math.max(minutes, maxMinutes));
    }

    private Double parseHourValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (Character.isDigit(value.charAt(0))) {
            return parseDouble(value);
        }
        return parseChineseNumber(value);
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    private Double parseChineseNumber(String value) {
        return switch (value) {
            case "半" -> 0.5;
            case "一" -> 1.0;
            case "二", "两" -> 2.0;
            case "三" -> 3.0;
            case "四" -> 4.0;
            case "五" -> 5.0;
            case "六" -> 6.0;
            case "七" -> 7.0;
            case "八" -> 8.0;
            case "九" -> 9.0;
            case "十" -> 10.0;
            default -> null;
        };
    }

    private int roundToTen(int minutes) {
        return ((minutes + 5) / 10) * 10;
    }

    private int roundToFive(int value) {
        return Math.round(value / 5.0f) * 5;
    }

    private Integer resolveBalanceFactor(String text, DomainType domainType) {
        if (containsAny(text, "冲刺", "临近", "赶进度")) {
            return 35;
        }
        if (containsAny(text, "均衡", "长期", "稳定", "半年")) {
            return 65;
        }
        if (domainType == DomainType.ENGLISH_LEARNING) {
            return 70;
        }
        if (domainType == DomainType.PROGRAMMING_LANGUAGE) {
            return 60;
        }
        return 50;
    }

    private LocalDate resolveDeadline(String message, LocalDate today) {
        LocalDate explicitDate = parseExplicitDate(message, today);
        if (explicitDate != null && !explicitDate.isBefore(today)) {
            return explicitDate;
        }
        String text = normalize(message);
        if (containsAny(text, "一年", "12个月", "十二个月")) {
            return today.plusYears(1);
        }
        if (containsAny(text, "半年", "6个月", "六个月")) {
            return today.plusMonths(6);
        }
        if (containsAny(text, "三个月", "3个月")) {
            return today.plusMonths(3);
        }
        if (containsAny(text, "两个月", "二个月", "2个月")) {
            return today.plusMonths(2);
        }
        if (containsAny(text, "一个月", "1个月")) {
            return today.plusMonths(1);
        }
        return today.plusDays(30);
    }

    private LocalDate parseExplicitDate(String message, LocalDate today) {
        String text = message == null ? "" : message;
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(text);
        if (isoMatcher.find()) {
            try {
                return LocalDate.of(
                        Integer.parseInt(isoMatcher.group(1)),
                        Integer.parseInt(isoMatcher.group(2)),
                        Integer.parseInt(isoMatcher.group(3))
                );
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        Matcher chineseMatcher = CHINESE_DATE_PATTERN.matcher(text);
        if (chineseMatcher.find()) {
            try {
                int month = Integer.parseInt(chineseMatcher.group(1));
                int day = Integer.parseInt(chineseMatcher.group(2));
                LocalDate date = LocalDate.of(today.getYear(), Month.of(month), day);
                if (date.isBefore(today)) {
                    return date.plusYears(1);
                }
                return date;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveProjectName(String message, DomainType domainType) {
        String text = normalize(message);
        return switch (domainType) {
            case ENGLISH_LEARNING -> resolveEnglishTarget(message) + "分项提分计划";
            case PROGRAMMING_LANGUAGE -> {
                String topic = extractProgrammingTopic(message);
                if (text.contains("linux") && text.contains("c++")) {
                    yield "Linux C/C++ 求职实战计划";
                }
                if (containsAny(text, "实习", "求职", "工作")) {
                    yield topic + "求职能力提升计划";
                }
                if (containsAny(text, "项目", "全栈")) {
                    yield topic + "项目实战提升计划";
                }
                yield topic + "系统进阶计划";
            }
            case CHINESE_POSTGRAD_EXAM -> "考研重点科目推进计划";
            case GENERAL -> resolveShortTarget(message) + "推进计划";
        };
    }

    private String resolveProjectDescription(String message, DomainType domainType, LocalDate deadline) {
        String timeText = deadline == null ? "目标周期内" : deadline + " 前";
        String text = normalize(message);
        return switch (domainType) {
            case ENGLISH_LEARNING -> "围绕" + resolveEnglishTarget(message) + "目标拆分听说读写与词汇复盘，在" + timeText + "形成稳定训练节奏并优先补齐薄弱项。";
            case PROGRAMMING_LANGUAGE -> {
                String topic = extractProgrammingTopic(message);
                if (text.contains("linux") && text.contains("c++")) {
                    yield "围绕 Linux 与 C/C++ 全栈开发能力搭建学习路径，兼顾现代 C++、系统编程、项目实践和求职所需的基础巩固。";
                }
                if (containsAny(text, "实习", "求职", "工作")) {
                    yield "围绕" + topic + "求职能力拆分基础、核心机制、工程实践和复盘任务，帮助学习过程更贴近岗位要求。";
                }
                yield "围绕" + topic + "能力提升拆分基础学习、核心概念、工具使用和项目实践，在" + timeText + "完成可执行的阶段推进。";
            }
            case CHINESE_POSTGRAD_EXAM -> "围绕考研目标拆分数学、英语、政治、专业课与复盘任务，根据重点科目和可投入时间安排长期备考节奏。";
            case GENERAL -> "围绕目标拆分核心学习、实践输出、反馈修正和阶段复盘，在" + timeText + "形成可执行的推进计划。";
        };
    }

    private String resolveEnglishTarget(String message) {
        String text = normalize(message);
        if (text.contains("雅思")) {
            return "雅思";
        }
        if (text.contains("托福")) {
            return "托福";
        }
        if (text.contains("六级")) {
            return "英语六级";
        }
        if (text.contains("四级")) {
            return "英语四级";
        }
        return "英语";
    }

    private String extractProgrammingTopic(String message) {
        String text = normalize(message);
        if (text.contains("c++")) {
            return "C++";
        }
        if (text.contains("spring")) {
            return "Spring Boot";
        }
        if (text.contains("vue")) {
            return "Vue";
        }
        if (text.contains("java")) {
            return "Java";
        }
        if (text.contains("python")) {
            return "Python";
        }
        if (text.contains("javascript")) {
            return "JavaScript";
        }
        if (text.contains("go")) {
            return "Go";
        }
        return "编程";
    }

    private String resolveShortTarget(String message) {
        String value = normalizeDescription(message);
        if (value.length() > 18) {
            return value.substring(0, 18);
        }
        return value.isBlank() ? "新目标" : value;
    }

    private boolean hasTimeSignal(String message) {
        return parseExplicitDate(message, LocalDate.now()) != null
                || containsAny(normalize(message), "天", "周", "月", "年", "半年", "deadline", "考试");
    }

    private TaskDraft task(String title, String description, Integer weight, Integer minSessionMinutes, LocalDate beginDate, LocalDate deadline) {
        TaskDraft task = new TaskDraft();
        task.setTitle(title);
        task.setDescription(description);
        task.setWeight(weight);
        task.setMinSessionMinutes(minSessionMinutes);
        task.setBeginDate(beginDate);
        task.setDeadline(deadline);
        return task;
    }

    private String normalizeDescription(String message) {
        String value = message == null ? "" : message.trim();
        return value.isBlank() ? "由 AI 根据自然语言目标生成。" : value;
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim().toLowerCase();
    }

    private static class DailyAvailability {
        private final int baseMinutes;
        private final int maxMinutes;

        private DailyAvailability(int baseMinutes, int maxMinutes) {
            this.baseMinutes = baseMinutes;
            this.maxMinutes = maxMinutes;
        }
    }

    private record DayAvailability(boolean[] days, int minutes, int maxMinutes) {
    }

    private record HourAvailability(int minutes, int maxMinutes) {
    }
}
