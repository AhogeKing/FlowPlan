package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {

    // Project 是排期的根对象；Task、Plan、Setting 都围绕它展开。
    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("user_id")
    private Integer userId;

    private String name;

    private String description;

    @JsonProperty("begin_date")
    private LocalDate beginDate;

    @JsonProperty("finish_date")
    private LocalDate finishDate;

    private LocalDate deadline;

    private String status = "NOT_STARTED";

    @JsonProperty("risk_level")
    private String riskLevel = "OK";

    // 上游字段变化后置为 true，提示计划模块需要重新生成后续安排。
    @JsonProperty("need_replan")
    private Boolean needReplan = false;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
