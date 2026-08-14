package com.xd.model.vo;

import lombok.Data;

import java.util.Map;

@Data
public class AgentEventVO {

    private String messageId;
    private String runId;

    private String taskId;

    private String sessionId;

    private String agentName;

    private String parentAgent;

    private String event;

    private String step;
    private String status;

    private String agentStatus;

    private Map<String, Object> output;

    private Long timestamp;
}