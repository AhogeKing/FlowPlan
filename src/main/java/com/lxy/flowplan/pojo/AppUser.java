package com.lxy.flowplan.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {

    // 所有业务数据都通过 user_id 归属到 AppUser，id 由数据库自增生成。
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;

    // 返回用户信息时永远不把密码散列暴露给前端。
    @JsonIgnore
    private String password;

    private String email;

    private String role = "USER";

    @JsonProperty("reg_time")
    private LocalDateTime regTime;

    public boolean isAdmin() {
        return role != null && role.equalsIgnoreCase("ADMIN");
    }
}
