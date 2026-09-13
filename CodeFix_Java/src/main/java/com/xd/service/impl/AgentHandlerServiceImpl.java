package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xd.context.AgentMessageProcessContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentActionDO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.enums.AgentActionCommandEnum;
import com.xd.model.enums.AgentActionStatusEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.PermissionDecisionEnum;
import com.xd.model.vo.AgentChatStreamVO;
import com.xd.model.vo.FileChangeVO;
import com.xd.mq.MessageHandler;
import com.xd.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service("AGENT_STATUS")
public class AgentHandlerServiceImpl implements MessageHandler {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentEventService agentEventService;
    @Autowired
    private AgentRunService agentRunService;
    @Autowired
    private AgentActionService agentActionService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private AgentSseService agentSseService;
    @Autowired
    private AgentChatAssemblerService agentChatAssemblerService;
    @Autowired
    private AgentFileChangeService agentFileChangeService;
    @Autowired
    private AgentConversationService agentConversationService;


    /**
     * MQ / Agent状态消息入口

     * Python -> Java
     */
    @Override
    public void handleMsg(String agentMessageJSON) {
        AgentMessageDTO messageDTO;
        // 1. JSON解析
        try {
            messageDTO = JSON.parseObject(agentMessageJSON, AgentMessageDTO.class);
        } catch (Exception e) {
            log.error("Agent消息JSON解析失败: {}", agentMessageJSON, e);
            throw e;
        }

        log.info("收到Agent事件: taskId={}, runId={}, agent={}, event={}, step={}, status={}",
                messageDTO.getTaskId(), messageDTO.getRunId(), messageDTO.getAgentName(),
                messageDTO.getEvent(), messageDTO.getStep(), messageDTO.getStatus());

        AgentMessageProcessContext messageProcessContext;
        try {
            messageProcessContext = transactionTemplate.execute(status -> {
                AgentMessageProcessContext.AgentMessageProcessContextBuilder builder = AgentMessageProcessContext.builder();

                // 2. 历史事件
                AgentEventDO eventDO = agentEventService.insertAgentEvent(messageDTO);
                if (eventDO == null) return builder.duplicate(true).build();

                // 3. 当前 Task 状态
                agentTaskService.handleAgentEvent(messageDTO);

                // 4. 更新当前 Run ActionId
                agentRunService.updateRun(messageDTO);

                // 5. Tool Action Runtime
                String actionId = messageDTO.getActionId();
                if (actionId != null && !actionId.isBlank()) {
                    AgentEventEnum event = AgentEventEnum.valueOf(messageDTO.getEvent());
                    // 创建 Tool Action

                    if (AgentEventEnum.TOOL_WAITING == event) {
                        agentActionService.createAction(messageDTO);
                    }

                    //Tool 真正开始执行
                    if (AgentEventEnum.TOOL_CALL == event) {
                        AgentActionDO action = agentActionService.getByActionId(actionId);
                        if (action != null) {
                            agentActionService.transition(actionId, AgentActionStatusEnum.EXECUTING);
                        }
                    }
                    // Tool执行结果
                    if (AgentEventEnum.TOOL_RESULT == event) {
                        AgentActionDO action = agentActionService.getByActionId(actionId);
                        if (action != null) {
                            agentActionService.transition(actionId, resolveActionResultStatus(messageDTO));
                        }
                    }
                }

                // 6. Permission
                PermissionDecisionEnum permissionDecision = permissionService.evaluate(messageDTO);
                builder.permissionDecision(permissionDecision);

                if (permissionDecision == null || permissionDecision == PermissionDecisionEnum.NONE) {
                    return builder.build();
                }

                // 7. Action Permission
                if (messageDTO.getActionId() != null && AgentEventEnum.TOOL_WAITING.eventDesc.equals(messageDTO.getEvent())) {
                    agentActionService.updatePermission(actionId, permissionDecision);
                    // 自动允许
                    if (permissionDecision == PermissionDecisionEnum.ALLOW) {
                        agentActionService.transition(actionId, AgentActionStatusEnum.APPROVED);
                    }
                    // 自动拒绝
                    if (permissionDecision == PermissionDecisionEnum.DENY) {
                        agentActionService.transition(actionId, AgentActionStatusEnum.REJECTED);
                    }
                    // 等待人工审批
                    if (permissionDecision == PermissionDecisionEnum.ASK) {
                        agentActionService.transition(actionId, AgentActionStatusEnum.WAITING_APPROVAL);
                    }
                }

                // 8. ASK 才需要前端审批
                if (permissionDecision != PermissionDecisionEnum.ASK) return builder.build();

                // 9. 文件变动信息解析并持久化
                FileChangeVO fileChange = agentFileChangeService.parse(messageDTO);
                if (fileChange != null) {
                    String diffId = agentFileChangeService.save(messageDTO, eventDO, fileChange);
                    builder.diffId(diffId);
                }

                // 10. 只有 Root Agent FINISH 才生成 Assistant ChatMessage
                agentConversationService.saveAssistantMessage(messageDTO);

                return builder.build();
            });
        } catch (Exception e) {
            log.info("Agent事件持久化失败: taskId={}, runId={}, messageId={}",
                    messageDTO.getTaskId(), messageDTO.getRunId(), messageDTO.getMessageId(), e);
            throw e;
        }
        if (messageProcessContext.isDuplicate()) return;
        PermissionDecisionEnum permissionDecision = messageProcessContext.getPermissionDecision();
        if (permissionDecision == PermissionDecisionEnum.ALLOW) {
            handleAllow(messageDTO);
            return;
        }
        if (permissionDecision == PermissionDecisionEnum.DENY) {
            handleDeny(messageDTO);
            return;
        }
        AgentChatStreamVO assemble = agentChatAssemblerService.assemble(messageDTO, messageProcessContext);
        agentSseService.send(assemble);
        log.debug("Agent事件持久化成功: taskId={}, runId={}, event={}", messageDTO.getTaskId(), messageDTO.getRunId(), messageDTO.getEvent());
    }

    private void handleAllow(AgentMessageDTO messageDTO) {
        if (messageDTO.getActionId() == null || messageDTO.getActionId().isBlank()) return;

        // Root Agent 继续使用原来的全局审批链路
        if (messageDTO.getParentAgent() == null || messageDTO.getParentAgent().isBlank()) {
            agentTaskService.handleCommand(
                    messageDTO.getTaskId(),
                    messageDTO.getRunId(),
                    messageDTO.getActionId(),
                    AgentActionCommandEnum.APPROVE.commandDesc_EN
            );
            return;
        }

        // Child Agent 只操作 Action，不修改 Global Task
        sendAgentCommand(messageDTO, AgentActionCommandEnum.APPROVE.commandDesc_EN);
    }

    private void handleDeny(AgentMessageDTO messageDTO) {
        if (messageDTO.getActionId() == null || messageDTO.getActionId().isBlank()) return;

        // Root Agent 继续使用原来的全局审批链路
        if (messageDTO.getParentAgent() == null || messageDTO.getParentAgent().isBlank()) {
            agentTaskService.handleCommand(
                    messageDTO.getTaskId(),
                    messageDTO.getRunId(),
                    messageDTO.getActionId(),
                    AgentActionCommandEnum.REJECT.commandDesc_EN
            );
            return;
        }

        // Child Agent 只操作 Action，不修改 Global Task
        sendAgentCommand(messageDTO, AgentActionCommandEnum.REJECT.commandDesc_EN);
    }

    private void sendAgentCommand(AgentMessageDTO messageDTO, String command) {
        // 这里不要走 AgentTaskService.handleCommand，因为 Child Agent 不应该修改 Task 全局状态
        agentTaskService.sendCommand(
                messageDTO.getTaskId(),
                messageDTO.getRunId(),
                messageDTO.getActionId(),
                command
        );
    }

    private AgentActionStatusEnum resolveActionResultStatus(AgentMessageDTO messageDTO) {
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
}