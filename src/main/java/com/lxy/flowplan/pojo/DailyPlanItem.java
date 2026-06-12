package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@TableName("daily_plan_item")
public class DailyPlanItem {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("plan_id")
    private Integer planId;

    @JsonProperty("task_id")
    private Integer taskId;

    // 当天建议投入时间，由计划生成逻辑写入。
    @JsonProperty("recommended_minutes")
    private Integer recommendedMinutes = 0;

    // 当天实际投入时间，由打卡记录汇总回填。
    @JsonProperty("actual_minutes")
    private Integer actualMinutes = 0;

    @JsonProperty("sort_order")
    private Integer sortOrder = 0;

    private String status = "NOT_DONE";

    private String reason;

    @TableField(exist = false)
    @JsonProperty("checkin_record")
    private CheckinRecord checkinRecord;
}
