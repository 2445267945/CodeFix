package com.xd.model.dto;

import lombok.Data;

@Data
public class AgentCommandDTO {

    /**
     * 当前 Session
     */
    private String sessionId;

    /**
     * 当前 task
     */
    private String taskId;

    /**
     * 当前 Run
     */
    private String runId;

    /**
     * 当前需要审批的 Action。
     * START / RETRY / CANCEL 等 Run Command 可以为空。
     */
    private String actionId;

    /**
     * 命令：
     * APPROVE / REJECT
     */
    private String command;
}