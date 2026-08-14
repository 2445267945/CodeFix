package com.xd.model.entity;

import lombok.Data;

@Data
public class TaskResultDO {

    private String taskId;
    private String runId;
    private Integer taskStatus;

    private String messageId;
    private String event;
    private String output;
    private Long eventTimestamp;
}