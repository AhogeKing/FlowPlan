package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @JsonProperty("user_id")
    private Integer userId;

    private String username;

    private String module;

    @JsonProperty("operation_type")
    private String operationType;

    private String description;

    @JsonProperty("request_method")
    private String requestMethod;

    @JsonProperty("request_url")
    private String requestUrl;

    private String ip;

    private String status;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("create_time")
    private LocalDateTime createTime;
}
