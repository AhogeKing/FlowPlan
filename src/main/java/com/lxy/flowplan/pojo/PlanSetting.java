package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plan_setting")
public class PlanSetting {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("user_id")
    private Integer userId;

    private String scope = "GLOBAL";

    @JsonProperty("project_id")
    private Integer projectId;

    @JsonProperty("base_daily_minutes")
    private Integer baseDailyMinutes = 120;

    @JsonProperty("mon_ratio")
    private Integer monRatio = 100;

    @JsonProperty("tue_ratio")
    private Integer tueRatio = 100;

    @JsonProperty("wed_ratio")
    private Integer wedRatio = 100;

    @JsonProperty("thu_ratio")
    private Integer thuRatio = 100;

    @JsonProperty("fri_ratio")
    private Integer friRatio = 100;

    @JsonProperty("sat_ratio")
    private Integer satRatio = 100;

    @JsonProperty("sun_ratio")
    private Integer sunRatio = 100;

    @JsonProperty("daily_min_minutes")
    private Integer dailyMinMinutes = 20;

    @JsonProperty("daily_max_minutes")
    private Integer dailyMaxMinutes = 120;

    @JsonProperty("task_min_count_per_day")
    private Integer taskMinCountPerDay = 1;

    @JsonProperty("task_max_count_per_day")
    private Integer taskMaxCountPerDay = 4;

    @JsonProperty("min_plan_item_minutes")
    private Integer minPlanItemMinutes = 20;

    @JsonProperty("max_plan_item_minutes")
    private Integer maxPlanItemMinutes = 120;

    @JsonProperty("time_block_minutes")
    private Integer timeBlockMinutes = 10;

    @JsonProperty("balance_factor")
    private Integer balanceFactor = 50;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
