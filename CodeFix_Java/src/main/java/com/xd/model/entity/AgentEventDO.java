package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentEventDO {

    private Long id;

    private String messageId;

    private String taskId;
    private String runId;

    private String sessionId;

    private String agentName;

    private String parentAgent;

    private String event;

    private String step;

    private String status;

    private String output;

    private Long eventTimestamp;
}