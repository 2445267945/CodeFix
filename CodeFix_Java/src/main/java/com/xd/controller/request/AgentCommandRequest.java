package com.xd.controller.request;

import lombok.Data;

@Data
public class AgentCommandRequest {

    /**
     * 当前 Run
     */
    private String runId;

    /**
     * 命令：
     * APPROVE / REJECT / CANCEL / RESUME ...
     */
    private String command;
}