package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRow {

    @JsonProperty("user_id")
    private Integer userId;

    private String username;

    private String role;

    @JsonProperty("project_count")
    private Long projectCount;

    @JsonProperty("register_time")
    private LocalDateTime registerTime;

    @JsonProperty("last_login")
    private LocalDateTime lastLogin;
}
