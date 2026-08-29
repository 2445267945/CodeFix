package com.xd.model.enums;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum PermissionProfileEnum {

    READ_ONLY(1, "READ_ONLY", "只允许读取和查询，不允许修改文件"),

    WORKSPACE(2, "WORKSPACE", "允许在当前 Workspace 内进行文件修改"),

    FULL_AUTO(3, "FULL_AUTO", "允许 Agent 自动执行全部已支持操作");

    public final Integer code;
    public final String desc;
    public final String description;
}
