package com.xd.model.enums;


public enum PermissionDecisionEnum {
    ALLOW(1, "ALLOW"),
    ASK(2, "ASK"),
    DENY(3, "DENY")
    ;

    public final Integer code;
    public final String desc;

    private PermissionDecisionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PermissionDecisionEnum getByDesc(String desc) {
        if (desc == null) return null;
        for (PermissionDecisionEnum value : PermissionDecisionEnum.values()) {
            if (value.desc.equals(desc)) {
                return value;
            }
        }
        return null;
    }
}
