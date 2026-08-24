package com.xd.model.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum AgentActionCommandEnum {

    APPROVE(1, "APPROVE", "允许执行"),
    REJECT(2, "REJECT", "拒绝执行");

    public final Integer command;
    public final String commandDesc_EN;
    public final String commandDesc_ZH;
}
