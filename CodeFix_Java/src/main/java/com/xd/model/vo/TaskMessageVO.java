package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskMessageVO {

    private String taskId;

    private String sessionId;

    private String runId;

    private String messageId;

    private String status;

    private ChatMessageVO message;
}