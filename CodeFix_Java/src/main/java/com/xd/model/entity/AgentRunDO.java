package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentRunDO {

    private Long id;

    private String runId;

    private String taskId;
    private String actionId;

    private String sessionId;
    private String permissionProfile;
    private Integer attempt;

    private Integer status;

    private Long startedAt;

    private Long endedAt;

    private String errorMessage;

    private Long createdAt;
}