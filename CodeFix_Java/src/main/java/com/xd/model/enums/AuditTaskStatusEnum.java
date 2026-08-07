package com.xd.model.enums;

public enum AuditTaskStatusEnum {
    // ===== 生命周期态 =====
    CREATED(1, "新建任务"),
    QUEUED(2, "任务排队"),
    COMPLETED(9, "任务结束"),

    // ===== 运行态 =====
    AGENT_THINKING(3, "AI推理中"),
    TOOL_CALLING(7, "工具执行中"),

    // ===== 阻塞态（通用） =====
    BLOCKED(4, "外部阻塞"),

    // ===== 特殊阻塞子类型（可独立或作为 BLOCKED 的原因） =====
    WAITING_HUMAN(5, "人工确认阻塞"),
    WAITING_RETRY(6, "任务重试阻塞"),

    // ===== 终态 =====
    NEED_RETRY(8, "任务失败（需重试）")
    ;

    public final Integer statusCode;
    public final String statusDesc;

    private AuditTaskStatusEnum(Integer statusCode, String statusDesc) {
        this.statusCode = statusCode;
        this.statusDesc = statusDesc;
    }

    public static AuditTaskStatusEnum getDestByCode(Integer code) {
        if (code == null) return null;
        for (AuditTaskStatusEnum status : AuditTaskStatusEnum.values()) {
            if (status.statusCode.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
