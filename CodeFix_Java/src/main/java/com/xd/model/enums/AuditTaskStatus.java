package com.xd.model.enums;

public enum AuditTaskStatus {
    CREATED(1, "新建任务"),
    QUEUED(2, "任务排队"),             // 排队等Worker
    AGENT_THINKING(3, "任务执行"),     // Python正在推理（对应CPU的Running）
    WAITING_VALIDATION(4, "任务工具阻塞"), // 阻塞（如Java做AST校验等操作）
    WAITING_HUMAN(5, "人工确认阻塞"),      // 等人工确认（可能阻塞很久！）
    WAITING_RETRY(6, "任务重试阻塞"),      // 等重试调度（带退避时间）
    TOOL_CALLING(7, "工具执行"),       // 正在调用外部工具（不是推理，是执行）
    NEED_RETRY(8, "任务失败"),         // 校验失败，需要带反馈重新推理
    COMPLETED(9, "任务结束")         // 终止
    ;

    public final Integer statusCode;
    public final String statusDesc;

    private AuditTaskStatus(Integer statusCode, String statusDesc) {
        this.statusCode = statusCode;
        this.statusDesc = statusDesc;
    }

    public static AuditTaskStatus getDestByCode(Integer code) {
        if (code == null) return null;
        for (AuditTaskStatus status : AuditTaskStatus.values()) {
            if (status.statusCode.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
