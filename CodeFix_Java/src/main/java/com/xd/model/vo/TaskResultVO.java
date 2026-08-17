package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskResultVO {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 本次执行 Run ID
     */
    private String runId;

    /**
     * 当前 Run 状态
     */
    private String status;

    /**
     * Codex 式 Chat 展示数据
     */
    private AgentChatViewVO chat;
}