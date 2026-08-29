package com.xd.runtime.permission;

import com.xd.model.dto.PermissionRuleDTO;

import java.util.List;

public interface PermissionRuntimeStore {

    /**
     * 获取当前 Run 的权限规则。
     */
    List<PermissionRuleDTO> getRules(String runId);

    /**
     * 保存权限规则。
     */
    void saveRule(String runId, PermissionRuleDTO rule);

    /**
     * 删除权限规则。
     */
    void deleteRule(String runId, PermissionRuleDTO rule);
}
