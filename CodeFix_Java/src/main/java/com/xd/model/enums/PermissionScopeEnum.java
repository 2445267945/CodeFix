package com.xd.model.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum PermissionScopeEnum {

    TOOL(
            1,
            "TOOL"
    ),

    PATH(
            2,
            "PATH"
    );

    public final Integer code;
    public final String desc;
}