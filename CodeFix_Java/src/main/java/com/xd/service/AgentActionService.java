package com.xd.service;

import com.xd.mapper.AgentActionMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentActionDO;
import com.xd.model.enums.AgentActionStatusEnum;
import com.xd.model.enums.PermissionDecisionEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

public interface AgentActionService {

    AgentActionDO getByActionId(String actionId);

    AgentActionDO getCurrentAction(String runId);

    List<AgentActionDO> getActionsByRunId(String runId);

    AgentActionDO createAction(AgentMessageDTO messageDTO);

    void updatePermission(String actionId, PermissionDecisionEnum decision);

    void transition(String actionId, AgentActionStatusEnum targetStatus);

    void handleEvent(AgentMessageDTO messageDTO);

    void approve(String actionId);

    void reject(String actionId);
}