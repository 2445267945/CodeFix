package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentTaskDO {

    private Long id;

    private String taskId;

    private String sessionId;

    private String runId;

    private String version;

    private String question;

    private String code;

    private String smells;

    private Integer status;

    private Long createdAt;

    private Long updatedAt;
}