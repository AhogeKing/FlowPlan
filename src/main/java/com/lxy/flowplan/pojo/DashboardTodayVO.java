package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardTodayVO {

    private LocalDate today;

    private String weekday;

    private String greeting;

    private DashboardSummary summary;

    @JsonProperty("active_projects")
    private List<DashboardActiveProject> activeProjects;

    @JsonProperty("today_plans")
    private List<DashboardTodayPlan> todayPlans;

    @JsonProperty("ai_suggestion")
    private DashboardAiSuggestion aiSuggestion;

    @JsonProperty("recent_stats")
    private DashboardRecentStats recentStats;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummary {

        @JsonProperty("total_recommended_minutes")
        private Integer totalRecommendedMinutes;

        @JsonProperty("total_completed_minutes")
        private Integer totalCompletedMinutes;

        @JsonProperty("total_plan_item_count")
        private Integer totalPlanItemCount;

        @JsonProperty("completed_plan_item_count")
        private Integer completedPlanItemCount;

        @JsonProperty("active_project_count")
        private Integer activeProjectCount;

        @JsonProperty("pressure_level")
        private String pressureLevel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardTodayPlan {

        @JsonProperty("project_id")
        private Integer projectId;

        @JsonProperty("project_name")
        private String projectName;

        @JsonProperty("recommended_minutes")
        private Integer recommendedMinutes;

        @JsonProperty("completed_minutes")
        private Integer completedMinutes;

        @JsonProperty("progress_rate")
        private Integer progressRate;

        private List<DashboardTodayPlanItem> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardTodayPlanItem {

        @JsonProperty("plan_item_id")
        private Integer planItemId;

        @JsonProperty("task_id")
        private Integer taskId;

        @JsonProperty("task_name")
        private String taskName;

        @JsonProperty("recommended_minutes")
        private Integer recommendedMinutes;

        @JsonProperty("actual_minutes")
        private Integer actualMinutes;

        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardActiveProject {

        @JsonProperty("project_id")
        private Integer projectId;

        @JsonProperty("project_name")
        private String projectName;

        private LocalDate deadline;

        @JsonProperty("remaining_days")
        private Integer remainingDays;

        @JsonProperty("progress_rate")
        private Integer progressRate;

        @JsonProperty("risk_level")
        private String riskLevel;

        private String status;

        @JsonProperty("need_replan")
        private Boolean needReplan;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardRecentStats {

        @JsonProperty("range_days")
        private Integer rangeDays;

        @JsonProperty("completion_rate")
        private Integer completionRate;

        @JsonProperty("study_minutes")
        private Integer studyMinutes;

        @JsonProperty("current_streak")
        private Integer currentStreak;

        @JsonProperty("completed_item_count")
        private Integer completedItemCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardAiSuggestion {

        private String focus;

        private String suggestion;

        private String motivation;
    }
}
