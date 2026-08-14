package com.xd.model.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AgentCommandEnum {
    START(1, "START", "开始执行"),
    RESUME(1, "RESUME", "断点重试"),
    RETRY(1, "RETRY", "重新执行"),
    CANCEL(1, "CANCEL", "取消任务")
    ;

    public final Integer command;
    public final String commandDesc_EN;
    public final String commandDesc_ZH;
}
