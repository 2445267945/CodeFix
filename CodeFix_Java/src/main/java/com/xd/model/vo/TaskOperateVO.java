package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskOperateVO {

    private String taskId;
    private String runId;

    private Integer status;

    private String statusValue;

    private String message;
}