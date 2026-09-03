package com.xd.model.dto;

import lombok.Data;

@Data
public class TaskHeartbeatTimeoutUpdateDTO {

    private String taskId;

    private String runId;

    private Integer currentStatus;

    private Integer nextStatus;

    private Long updatedAt;
    private Long lastHeartbeatAt;
}