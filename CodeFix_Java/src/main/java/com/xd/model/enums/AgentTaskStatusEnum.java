package com.xd.model.enums;

import java.util.Set;

public enum AgentTaskStatusEnum {
    // ===== 生命周期态 =====
    CREATED(1, "CREATED", "新建任务"),
    QUEUED(2, "QUEUED", "任务排队"),
    // ===== 运行态 =====
    AGENT_THINKING(3, "THINKING", "AI推理中"),
    TOOL_CALLING(4, "EXECUTING", "工具执行中"),
    // ===== 阻塞态（通用） =====

    WAITING_HUMAN(5, "WAITING_HUMAN", "等待人工确认"),
    // ===== 终态 =====
    NEED_RETRY(6, "ERROR", "任务失败，需重试"),
    COMPLETED(7, "FINISHED", "任务结束"),
    CANCELLED(8, "CANCELLED", "任务取消")
    ;

    public final Integer statusCode;
    public final String statusDesc_EN;
    public final String statusDesc_ZH;

    private static final Set<Integer> HEARTBEAT_REQUIRED_STATUS = Set.of(
            AgentTaskStatusEnum.AGENT_THINKING.statusCode,
            AgentTaskStatusEnum.TOOL_CALLING.statusCode
    );

    public boolean requiresHeartbeat() {
        return this == AGENT_THINKING || this == TOOL_CALLING;
    }

    private AgentTaskStatusEnum(Integer statusCode, String statusDesc_EN, String statusDesc_ZH) {
        this.statusCode = statusCode;
        this.statusDesc_EN = statusDesc_EN;
        this.statusDesc_ZH = statusDesc_ZH;
    }

    public static AgentTaskStatusEnum getStatusByCode(Integer code) {
        if (code == null) return null;
        for (AgentTaskStatusEnum status : AgentTaskStatusEnum.values()) {
            if (status.statusCode.equals(code)) {
                return status;
            }
        }
        return null;
    }

    public static AgentTaskStatusEnum getStatusByDesc(String desc) {
        if (desc == null) return null;
        for (AgentTaskStatusEnum status : AgentTaskStatusEnum.values()) {
            if (status.statusDesc_ZH.equals(desc) || status.statusDesc_EN.equals(desc)) {
                return status;
            }
        }
        return null;
    }
}
