package com.xd.model.dto;

import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.model.enums.PermissionScopeEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionRuleDTO {

    /**
     * 当前规则适用于哪个 Tool。
     */
    private String toolName;

    /**
     * 规则决定。
     */
    private PermissionDecisionEnum decision;

    /**
     * 规则作用域。
     *
     * 第一版先支持：
     * TOOL
     * PATH
     */
    private PermissionScopeEnum scope;

    /**
     * 当 scope = PATH 时使用。
     *
     * 例如：
     * src/**
     */
    private String pattern;
}