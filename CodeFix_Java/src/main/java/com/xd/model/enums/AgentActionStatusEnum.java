package com.xd.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AgentActionStatusEnum {

    PENDING("PENDING", "待执行"),
    WAITING_APPROVAL("WAITING_APPROVAL", "等待审批"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已拒绝"),
    EXECUTING("EXECUTING", "执行中"),
    SUCCEEDED("SUCCEEDED", "执行成功"),
    FAILED("FAILED", "执行失败"),
    CANCELLED("CANCELLED", "已取消");

    public final String code;
    public final String desc;

    public static AgentActionStatusEnum getStatusByCode(String code) {
        if (code == null) return null;
        for (AgentActionStatusEnum status : AgentActionStatusEnum.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
