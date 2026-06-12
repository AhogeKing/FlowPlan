package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {

    // Task 是 Project 下的最小排期单元，id 由数据库自增生成。
    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("project_id")
    private Integer projectId;

    private String title;

    private String description;

    // V1.1 通过权重分配 Project 总预算，不再在 Task 上维护总估时/已完成分钟。
    private Integer weight = 1;

    @JsonProperty("min_session_minutes")
    private Integer minSessionMinutes;

    @JsonProperty("begin_date")
    private LocalDate beginDate;

    private LocalDate deadline;

    @JsonProperty("dependency_task_id")
    private Integer dependencyTaskId;

    // doneFlag 是用户手动完成标记，status 是面向界面的简化状态。
    @JsonProperty("done_flag")
    private Boolean doneFlag = false;

    private String status = "NOT_STARTED";

    // 创建和更新时间由数据库默认值或服务端维护。
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
