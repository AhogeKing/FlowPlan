package com.lxy.flowplan.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lxy.flowplan.dto.ai.AiProjectDraft;
import com.lxy.flowplan.dto.ai.DomainType;
import com.lxy.flowplan.dto.ai.PlanSettingDraft;
import com.lxy.flowplan.dto.ai.ProjectDraft;
import com.lxy.flowplan.dto.ai.TaskDraft;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class AiDraftSanitizer {
    private final ObjectMapper objectMapper;

    public AiDraftSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiProjectDraft parseDraft(String content) {
        try {
            String json = extractJson(content);
            return objectMapper.readValue(json, AiProjectDraft.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 返回 JSON 解析失败", e);
        }
    }

    public AiProjectDraft sanitize(AiProjectDraft draft, AiProjectDraft fallback, DomainType domainType, LocalDate today) {
        return sanitize(draft, fallback, domainType, today, false);
    }

    public AiProjectDraft sanitizeForPreview(AiProjectDraft draft, AiProjectDraft fallback, DomainType domainType, LocalDate today) {
        return sanitize(draft, fallback, domainType, today, true);
    }

    private AiProjectDraft sanitize(AiProjectDraft draft,
                                    AiProjectDraft fallback,
                                    DomainType domainType,
                                    LocalDate today,
                                    boolean normalizeRatios) {
        AiProjectDraft target = draft == null ? fallback : draft;
        if (target == null) {
            throw new IllegalArgumentException("AI 草案不能为空");
        }

        List<String> warnings = new ArrayList<>();
        if (target.getWarnings() != null) {
            target.getWarnings().stream()
                    .filter(warning -> warning != null && !warning.isBlank())
                    .map(String::trim)
                    .map(this::normalizeUserFacingWarning)
                    .filter(warning -> warning != null && !warning.isBlank())
                    .forEach(warnings::add);
        }

        ProjectDraft fallbackProject = fallback != null ? fallback.getProject() : null;
        ProjectDraft project = sanitizeProject(target.getProject(), fallbackProject, today, warnings);
        target.setProject(project);

        List<TaskDraft> fallbackTasks = fallback != null ? fallback.getTasks() : List.of();
        target.setTasks(sanitizeTasks(target.getTasks(), fallbackTasks, project, warnings));
        target.setSetting(sanitizeSetting(target.getSetting(), fallback != null ? fallback.getSetting() : null, warnings, normalizeRatios));

        if (target.getExplanation() == null || target.getExplanation().isBlank()) {
            target.setExplanation("已根据你的目标生成项目、任务和计划偏好草案，确认后会生成每日计划。");
        } else {
            target.setExplanation(trimToLength(target.getExplanation(), 500));
        }
        target.setDomainType(domainType);
        target.setWarnings(toDistinctUserFacingWarnings(warnings));
        return target;
    }

    private ProjectDraft sanitizeProject(ProjectDraft project, ProjectDraft fallback, LocalDate today, List<String> warnings) {
        ProjectDraft target = project == null ? new ProjectDraft() : project;
        if (target.getName() == null || target.getName().isBlank()) {
            target.setName(fallback != null ? fallback.getName() : "AI 生成项目");
            warnings.add("项目名称缺失，已使用默认名称。");
        }
        target.setName(trimToLength(target.getName().trim(), 100));

        if (target.getDescription() == null || target.getDescription().isBlank()) {
            target.setDescription(fallback != null ? fallback.getDescription() : "由 AI 根据自然语言目标生成。");
        } else {
            target.setDescription(trimToLength(target.getDescription().trim(), 500));
        }

        if (target.getBeginDate() == null) {
            target.setBeginDate(fallback != null && fallback.getBeginDate() != null ? fallback.getBeginDate() : today);
            warnings.add("项目开始日期缺失，已使用今天。");
        }
        if (target.getDeadline() == null) {
            target.setDeadline(fallback != null && fallback.getDeadline() != null ? fallback.getDeadline() : target.getBeginDate().plusDays(30));
            warnings.add("项目截止日期缺失，已使用默认截止日期。");
        }
        if (target.getDeadline().isBefore(target.getBeginDate())) {
            target.setDeadline(target.getBeginDate().plusDays(30));
            warnings.add("项目截止日期早于开始日期，已调整为开始日期后 30 天。");
        }
        return target;
    }

    private List<TaskDraft> sanitizeTasks(List<TaskDraft> tasks, List<TaskDraft> fallbackTasks, ProjectDraft project, List<String> warnings) {
        List<TaskDraft> source = tasks == null || tasks.isEmpty() ? fallbackTasks : tasks;
        List<TaskDraft> result = new ArrayList<>();

        for (TaskDraft task : source) {
            if (task == null) {
                continue;
            }
            TaskDraft sanitized = sanitizeTask(task, project);
            if (sanitized != null) {
                result.add(sanitized);
            }
            if (result.size() == 8) {
                warnings.add("任务数量超过 8 个，已保留前 8 个任务。");
                break;
            }
        }

        while (result.size() < 2) {
            TaskDraft task = new TaskDraft();
            task.setTitle(result.isEmpty() ? "核心学习" : "阶段复盘");
            task.setDescription(result.isEmpty() ? "围绕目标进行主要学习和练习。" : "总结阶段进展并调整后续计划。");
            task.setWeight(result.isEmpty() ? 4 : 2);
            task.setMinSessionMinutes(30);
            task.setBeginDate(project.getBeginDate());
            task.setDeadline(project.getDeadline());
            result.add(task);
            warnings.add("任务数量不足，已补充默认任务。");
        }
        return result;
    }

    private TaskDraft sanitizeTask(TaskDraft task, ProjectDraft project) {
        if (task.getTitle() == null || task.getTitle().isBlank()) {
            return null;
        }
        TaskDraft target = new TaskDraft();
        target.setTitle(trimToLength(task.getTitle().trim(), 100));
        target.setDescription(task.getDescription() == null ? null : trimToLength(task.getDescription().trim(), 500));
        target.setWeight(clamp(task.getWeight() == null ? 1 : task.getWeight(), 1, 5));
        target.setMinSessionMinutes(roundUpToTen(clamp(task.getMinSessionMinutes() == null ? 30 : task.getMinSessionMinutes(), 10, 180)));

        LocalDate beginDate = task.getBeginDate() == null ? project.getBeginDate() : task.getBeginDate();
        if (beginDate.isBefore(project.getBeginDate())) {
            beginDate = project.getBeginDate();
        }
        if (beginDate.isAfter(project.getDeadline())) {
            beginDate = project.getBeginDate();
        }
        target.setBeginDate(beginDate);

        LocalDate deadline = task.getDeadline() == null ? project.getDeadline() : task.getDeadline();
        if (deadline.isAfter(project.getDeadline())) {
            deadline = project.getDeadline();
        }
        if (deadline.isBefore(beginDate)) {
            deadline = project.getDeadline();
        }
        target.setDeadline(deadline);
        return target;
    }

    private PlanSettingDraft sanitizeSetting(PlanSettingDraft setting, PlanSettingDraft fallback, List<String> warnings, boolean normalizeRatios) {
        PlanSettingDraft target = setting == null ? new PlanSettingDraft() : setting;
        PlanSettingDraft defaults = fallback == null ? defaultSetting() : fallback;

        target.setBaseDailyMinutes(positiveOrDefault(target.getBaseDailyMinutes(), defaults.getBaseDailyMinutes()));
        target.setDailyMinMinutes(nonNegativeOrDefault(target.getDailyMinMinutes(), defaults.getDailyMinMinutes()));
        target.setDailyMaxMinutes(positiveOrDefault(target.getDailyMaxMinutes(), defaults.getDailyMaxMinutes()));

        if (target.getDailyMinMinutes() > target.getBaseDailyMinutes()) {
            target.setDailyMinMinutes(Math.max(0, target.getBaseDailyMinutes()));
            warnings.add("dailyMinMinutes 大于 baseDailyMinutes，已自动下调。");
        }
        if (target.getDailyMaxMinutes() < target.getBaseDailyMinutes()) {
            target.setDailyMaxMinutes(target.getBaseDailyMinutes());
            warnings.add("dailyMaxMinutes 小于 baseDailyMinutes，已自动上调。");
        }

        target.setMonRatio(sanitizeRatio(target.getMonRatio(), defaults.getMonRatio(), normalizeRatios));
        target.setTueRatio(sanitizeRatio(target.getTueRatio(), defaults.getTueRatio(), normalizeRatios));
        target.setWedRatio(sanitizeRatio(target.getWedRatio(), defaults.getWedRatio(), normalizeRatios));
        target.setThuRatio(sanitizeRatio(target.getThuRatio(), defaults.getThuRatio(), normalizeRatios));
        target.setFriRatio(sanitizeRatio(target.getFriRatio(), defaults.getFriRatio(), normalizeRatios));
        target.setSatRatio(sanitizeRatio(target.getSatRatio(), defaults.getSatRatio(), normalizeRatios));
        target.setSunRatio(sanitizeRatio(target.getSunRatio(), defaults.getSunRatio(), normalizeRatios));

        target.setTaskMinCountPerDay(clamp(nonNegativeOrDefault(target.getTaskMinCountPerDay(), defaults.getTaskMinCountPerDay()), 1, 6));
        target.setTaskMaxCountPerDay(clamp(positiveOrDefault(target.getTaskMaxCountPerDay(), defaults.getTaskMaxCountPerDay()), 1, 6));
        if (target.getTaskMaxCountPerDay() < target.getTaskMinCountPerDay()) {
            target.setTaskMaxCountPerDay(target.getTaskMinCountPerDay());
            warnings.add("taskMaxCountPerDay 小于 taskMinCountPerDay，已自动上调。");
        }

        target.setMinPlanItemMinutes(roundUpToTen(positiveOrDefault(target.getMinPlanItemMinutes(), defaults.getMinPlanItemMinutes())));
        target.setMaxPlanItemMinutes(roundUpToTen(positiveOrDefault(target.getMaxPlanItemMinutes(), defaults.getMaxPlanItemMinutes())));
        if (target.getMaxPlanItemMinutes() < target.getMinPlanItemMinutes()) {
            target.setMaxPlanItemMinutes(target.getMinPlanItemMinutes());
            warnings.add("maxPlanItemMinutes 小于 minPlanItemMinutes，已自动上调。");
        }
        if (target.getDailyMaxMinutes() < target.getMinPlanItemMinutes()) {
            target.setDailyMaxMinutes(target.getMinPlanItemMinutes());
            warnings.add("dailyMaxMinutes 小于 minPlanItemMinutes，已自动上调。");
        }

        Integer timeBlock = target.getTimeBlockMinutes();
        target.setTimeBlockMinutes(timeBlock != null && timeBlock == 15 ? 15 : 10);
        target.setBalanceFactor(clamp(nonNegativeOrDefault(target.getBalanceFactor(), defaults.getBalanceFactor()), 0, 100));
        return target;
    }

    private PlanSettingDraft defaultSetting() {
        PlanSettingDraft setting = new PlanSettingDraft();
        setting.setBaseDailyMinutes(120);
        setting.setMonRatio(100);
        setting.setTueRatio(100);
        setting.setWedRatio(100);
        setting.setThuRatio(100);
        setting.setFriRatio(100);
        setting.setSatRatio(100);
        setting.setSunRatio(100);
        setting.setDailyMinMinutes(20);
        setting.setDailyMaxMinutes(180);
        setting.setTaskMinCountPerDay(1);
        setting.setTaskMaxCountPerDay(4);
        setting.setMinPlanItemMinutes(20);
        setting.setMaxPlanItemMinutes(120);
        setting.setTimeBlockMinutes(10);
        setting.setBalanceFactor(50);
        return setting;
    }

    private String extractJson(String content) {
        String value = content == null ? "" : content.trim();
        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace < firstBrace) {
            throw new IllegalArgumentException("AI 返回内容不是 JSON 对象");
        }
        return value.substring(firstBrace, lastBrace + 1);
    }

    private Integer positiveOrDefault(Integer value, Integer fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private Integer nonNegativeOrDefault(Integer value, Integer fallback) {
        return value != null && value >= 0 ? value : fallback;
    }

    private int roundUpToTen(int value) {
        return ((value + 9) / 10) * 10;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int sanitizeRatio(Integer value, Integer fallback, boolean normalize) {
        int ratio = clamp(nonNegativeOrDefault(value, fallback), 0, 300);
        if (!normalize) {
            return ratio;
        }
        return Math.round(ratio / 5.0f) * 5;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private List<String> toDistinctUserFacingWarnings(List<String> warnings) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String warning : warnings) {
            String normalized = normalizeUserFacingWarning(warning);
            if (normalized != null && !normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private String normalizeUserFacingWarning(String warning) {
        if (warning == null || warning.isBlank()) {
            return null;
        }

        String value = warning.trim();
        String lower = value.toLowerCase();
        if (value.contains("系统默认")
                || value.contains("模板")
                || value.contains("后端算法")
                || value.contains("内部")
                || value.contains("接口")
                || lower.contains("json")) {
            return null;
        }
        if (lower.contains("basedailyminutes")
                || lower.contains("dailymaxminutes")
                || lower.contains("dailyminminutes")
                || lower.contains("taskmaxcountperday")
                || lower.contains("taskmincountperday")
                || lower.contains("maxplanitemminutes")
                || lower.contains("minplanitemminutes")
                || lower.contains("timeblockminutes")
                || lower.contains("balancefactor")) {
            return "部分计划参数已根据你的目标自动校正。";
        }
        return trimToLength(value, 200);
    }
}
