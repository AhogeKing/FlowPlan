package com.lxy.flowplan.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminOperationLogPage {

    private Integer page;

    private Integer size;

    private Long total;

    @JsonProperty("total_pages")
    private Integer totalPages;

    private List<OperationLog> records;
}
