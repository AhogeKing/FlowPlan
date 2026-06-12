package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "checkin_record")
public class CheckinRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JsonProperty("plan_item_id")
    @Column(name = "plan_item_id", nullable = false)
    private Integer planItemId;

    @JsonProperty("task_id")
    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    @JsonProperty("created_at")
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JsonProperty("checkin_date")
    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @JsonProperty("completed_minutes")
    @Column(name = "completed_minutes", nullable = false)
    private Integer completedMinutes = 0;

    @Column(name = "note")
    private String note;
}
