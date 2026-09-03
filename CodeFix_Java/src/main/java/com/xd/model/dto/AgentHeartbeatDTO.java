package com.xd.model.dto;

import lombok.Data;

@Data
public class AgentHeartbeatDTO {

    private String version;

    private Long timestamp;

    private String messageId;

    private String sessionId;

    private String taskId;

    private String type;

    private String runId;

    private Long seq;
}