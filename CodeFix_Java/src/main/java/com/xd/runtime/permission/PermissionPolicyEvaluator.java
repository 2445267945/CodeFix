package com.xd.runtime.permission;

import com.xd.model.dto.PermissionRuleDTO;
import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.model.enums.PermissionProfileEnum;

import java.util.List;
import java.util.Map;

public interface PermissionPolicyEvaluator {

    PermissionDecisionEnum evaluate(PermissionProfileEnum profile, List<PermissionRuleDTO> rules, String toolName, Map<String, Object> arguments);
}
