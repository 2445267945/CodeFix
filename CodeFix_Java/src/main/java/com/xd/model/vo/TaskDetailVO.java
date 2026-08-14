package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskDetailVO {

    private String taskId;
    private String runId;

    private String sessionId;

    private Integer status;

    private String statusValue;

    private String question;

    private Long createdAt;

    private Long updatedAt;
}