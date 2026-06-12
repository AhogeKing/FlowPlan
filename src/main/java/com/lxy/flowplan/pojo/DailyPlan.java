package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("daily_plan")
public class DailyPlan {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("project_id")
    private Integer projectId;

    @JsonProperty("plan_date")
    private LocalDate planDate;

    // 当天建议投入总量，由计划明细汇总。
    @JsonProperty("total_recommended_minutes")
    private Integer totalRecommendedMinutes = 0;

    // 当天实际投入总量，由打卡记录汇总。
    @JsonProperty("total_actual_minutes")
    private Integer totalActualMinutes = 0;

    private String status = "NOT_DONE";
}
