package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentActionDO {

    private Long id;

    private String actionId;

    private String taskId;

    private String runId;

    private String agentName;

    private String parentAgent;

    private String toolName;

    private String toolCallId;

    private String status;

    private String permissionDecision;

    private Long createdAt;

    private Long updatedAt;

    private Long startedAt;

    private Long endedAt;
}
