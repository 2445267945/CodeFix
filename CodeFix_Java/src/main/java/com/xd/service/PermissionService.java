package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.enums.PermissionDecisionEnum;

public interface PermissionService {

    /**
     * 根据 Agent Tool 请求计算最终权限。
     */
    PermissionDecisionEnum evaluate(AgentMessageDTO messageDTO);

}