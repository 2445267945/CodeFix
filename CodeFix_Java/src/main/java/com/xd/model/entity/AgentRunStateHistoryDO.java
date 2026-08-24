package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentRunStateHistoryDO {

    private Long id;

    /**
     * 所属 Task
     */
    private String taskId;

    /**
     * 所属 Run
     */
    private String runId;

    /**
     * 状态变化前
     */
    private String fromStatus;

    /**
     * 触发来源
     *
     * EVENT / COMMAND / SYSTEM
     */
    private String triggerType;

    /**
     * 具体触发内容
     *
     * 例如：
     * TOOL_CALL
     * FINISH
     * CANCEL
     * RESUME
     */
    private String trigger;

    /**
     * 状态变化后
     */
    private String toStatus;

    /**
     * 对应的 Agent Event messageId
     *
     * 如果 triggerType = EVENT，
     * 可以关联具体 AgentEvent。
     *
     * COMMAND / SYSTEM 可以为空。
     */
    private String messageId;

    /**
     * Agent 当前 step
     *
     * 主要用于和 AgentEvent 对齐。
     */
    private Integer step;

    /**
     * 状态迁移原因
     */
    private String reason;

    /**
     * 状态迁移时间
     */
    private Long createdAt;
}
