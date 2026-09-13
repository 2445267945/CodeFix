package com.xd.service.impl;

import com.xd.mapper.AgentActionMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentActionDO;
import com.xd.model.enums.AgentActionStatusEnum;
import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.service.AgentActionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;


@Slf4j
@Service
public class AgentActionServiceImpl implements AgentActionService {

    @Autowired
    private AgentActionMapper agentActionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentActionDO createAction(AgentMessageDTO messageDTO) {
        if (messageDTO == null) {
            throw new IllegalArgumentException("AgentMessageDTO 不能为空");
        }

        if (messageDTO.getActionId() == null || messageDTO.getActionId().isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
        }

        AgentActionDO existing = agentActionMapper.selectByActionId(messageDTO.getActionId());
        if (existing != null) {
            return existing;
        }

        long now = System.currentTimeMillis();

        AgentActionDO action = new AgentActionDO();
        action.setActionId(messageDTO.getActionId());
        action.setTaskId(messageDTO.getTaskId());
        action.setRunId(messageDTO.getRunId());
        action.setAgentName(messageDTO.getAgentName());
        action.setParentAgent(messageDTO.getParentAgent());
        action.setToolCallId(extractToolCallId(messageDTO));
        action.setToolName(extractToolName(messageDTO));
        action.setStatus(AgentActionStatusEnum.WAITING_APPROVAL.code);
        action.setPermissionDecision(null);
        action.setCreatedAt(now);
        action.setUpdatedAt(now);

        agentActionMapper.insertAction(action);

        return action;
    }

    @Override
    public AgentActionDO getByActionId(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        return agentActionMapper.selectByActionId(actionId);
    }

    @Override
    public AgentActionDO getCurrentAction(String runId) {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        return agentActionMapper.selectCurrentByRunId(runId);
    }

    @Override
    public List<AgentActionDO> getActionsByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return Collections.emptyList();
        }
        return agentActionMapper.selectByRunId(runId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(String actionId, PermissionDecisionEnum decision) {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
        }

        if (decision == null) {
            throw new IllegalArgumentException("Permission decision 不能为空");
        }

        AgentActionDO action = agentActionMapper.selectByActionId(actionId);
        if (action == null) {
            throw new IllegalStateException("Action 不存在: actionId=" + actionId);
        }

        action.setPermissionDecision(decision.name());
        action.setUpdatedAt(System.currentTimeMillis());

        agentActionMapper.updateActionStatus(action);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(String actionId, AgentActionStatusEnum targetStatus) {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
        }

        if (targetStatus == null) {
            throw new IllegalArgumentException("Action 状态不能为空");
        }

        AgentActionDO action = agentActionMapper.selectByActionId(actionId);
        if (action == null) {
            throw new IllegalStateException("Action 不存在: actionId=" + actionId);
        }

        AgentActionStatusEnum currentStatus = AgentActionStatusEnum.getStatusByCode(action.getStatus());

        if (currentStatus == null) {
            throw new IllegalStateException(
                    "未知 Action 状态: actionId=" + actionId + ", status=" + action.getStatus()
            );
        }

        if (currentStatus == targetStatus) {
            return;
        }

        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                    "非法 Action 状态迁移: actionId=" + actionId
                            + ", current=" + currentStatus.code
                            + ", target=" + targetStatus.code
            );
        }

        long now = System.currentTimeMillis();

        action.setStatus(targetStatus.code);
        action.setUpdatedAt(now);

        if (targetStatus == AgentActionStatusEnum.EXECUTING && action.getStartedAt() == null) {
            action.setStartedAt(now);
        }

        if (targetStatus == AgentActionStatusEnum.SUCCEEDED
                || targetStatus == AgentActionStatusEnum.FAILED
                || targetStatus == AgentActionStatusEnum.REJECTED
                || targetStatus == AgentActionStatusEnum.CANCELLED) {
            action.setEndedAt(now);
        }

        agentActionMapper.updateActionStatus(action);

        log.info(
                "Agent Action 状态迁移: actionId={}, runId={}, {} -> {}",
                action.getActionId(),
                action.getRunId(),
                currentStatus.code,
                targetStatus.code
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleEvent(AgentMessageDTO messageDTO) {
        if (messageDTO == null) {
            return;
        }

        String actionId = messageDTO.getActionId();
        if (actionId == null || actionId.isBlank()) {
            return;
        }

        String event = messageDTO.getEvent();
        if (event == null || event.isBlank()) {
            return;
        }

        if ("TOOL_WAITING".equalsIgnoreCase(event)) {
            createAction(messageDTO);
            return;
        }

        AgentActionDO action = getByActionId(actionId);
        if (action == null) {
            log.warn(
                    "收到 Action Event 但 Action 不存在: actionId={}, event={}, runId={}",
                    actionId,
                    event,
                    messageDTO.getRunId()
            );
            return;
        }

        if ("TOOL_CALL".equalsIgnoreCase(event)) {
            transition(actionId, AgentActionStatusEnum.EXECUTING);
            return;
        }

        if ("TOOL_RESULT".equalsIgnoreCase(event)) {
            transition(actionId, resolveResultStatus(messageDTO));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String actionId) {
        AgentActionDO action = getRequiredAction(actionId);
        checkWaitingApproval(action);

        updatePermission(actionId, PermissionDecisionEnum.ALLOW);
        transition(actionId, AgentActionStatusEnum.APPROVED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String actionId) {
        AgentActionDO action = getRequiredAction(actionId);
        checkWaitingApproval(action);

        updatePermission(actionId, PermissionDecisionEnum.DENY);
        transition(actionId, AgentActionStatusEnum.REJECTED);
    }

    private AgentActionDO getRequiredAction(String actionId) {
        AgentActionDO action = getByActionId(actionId);
        if (action == null) {
            throw new IllegalStateException("Action 不存在: actionId=" + actionId);
        }
        return action;
    }

    private void checkWaitingApproval(AgentActionDO action) {
        AgentActionStatusEnum currentStatus = AgentActionStatusEnum.getStatusByCode(action.getStatus());

        if (currentStatus != AgentActionStatusEnum.WAITING_APPROVAL) {
            throw new IllegalStateException(
                    "当前 Action 不可审批: actionId=" + action.getActionId()
                            + ", status=" + action.getStatus()
            );
        }
    }

    private AgentActionStatusEnum resolveResultStatus(AgentMessageDTO messageDTO) {
        if (messageDTO.getOutput() == null) {
            return AgentActionStatusEnum.SUCCEEDED;
        }

        Object success = messageDTO.getOutput().get("success");
        if (success instanceof Boolean && !(Boolean) success) {
            return AgentActionStatusEnum.FAILED;
        }

        Object status = messageDTO.getOutput().get("status");
        if (status != null && "ERROR".equalsIgnoreCase(String.valueOf(status))) {
            return AgentActionStatusEnum.FAILED;
        }

        return AgentActionStatusEnum.SUCCEEDED;
    }

    private boolean canTransition(
            AgentActionStatusEnum currentStatus,
            AgentActionStatusEnum targetStatus
    ) {
        return switch (currentStatus) {
            case PENDING -> targetStatus == AgentActionStatusEnum.WAITING_APPROVAL
                    || targetStatus == AgentActionStatusEnum.APPROVED
                    || targetStatus == AgentActionStatusEnum.REJECTED
                    || targetStatus == AgentActionStatusEnum.CANCELLED;

            case WAITING_APPROVAL -> targetStatus == AgentActionStatusEnum.APPROVED
                    || targetStatus == AgentActionStatusEnum.REJECTED
                    || targetStatus == AgentActionStatusEnum.CANCELLED;

            case APPROVED -> targetStatus == AgentActionStatusEnum.EXECUTING
                    || targetStatus == AgentActionStatusEnum.CANCELLED;

            case EXECUTING -> targetStatus == AgentActionStatusEnum.SUCCEEDED
                    || targetStatus == AgentActionStatusEnum.FAILED
                    || targetStatus == AgentActionStatusEnum.CANCELLED;

            case REJECTED, SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }

    private String extractToolName(AgentMessageDTO messageDTO) {
        if (messageDTO.getOutput() == null) {
            return null;
        }

        Object tool = messageDTO.getOutput().get("tool");
        return tool == null ? null : String.valueOf(tool);
    }

    private String extractToolCallId(AgentMessageDTO messageDTO) {
        if (messageDTO.getOutput() == null) {
            return null;
        }

        Object toolCallId = messageDTO.getOutput().get("toolCallId");
        return toolCallId == null ? null : String.valueOf(toolCallId);
    }
}