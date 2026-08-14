package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.AuditTaskCreateDTO;
import com.xd.model.vo.AuditRequestVO;
import com.xd.model.vo.TaskCreateVO;
import com.xd.mq.MessageHandler;
import com.xd.service.AgentEventService;
import com.xd.service.AgentTaskService;
import com.xd.service.AuditHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service("AGENT_STATUS")
public class AuditHandlerServiceImpl implements AuditHandlerService, MessageHandler {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentEventService agentEventService;
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 兼容旧版接口。
     * 新前端应该直接调用 /api/audit/tasks。
     */
    @Override
    public String analyzeCode(AuditRequestVO request) {
        if (request == null
                || request.getCode() == null
                || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Java代码不能为空");
        }
        AuditTaskCreateDTO dto = new AuditTaskCreateDTO();
        dto.setCode(request.getCode());
        dto.setFileName(request.getFileName());
        dto.setSmells(request.getSmells());
        TaskCreateVO result = agentTaskService.createTask(dto);
        return result.getTaskId();
    }

    /**
     * MQ / Agent状态消息入口
     *
     * Python -> Java
     */
    @Override
    public void handleMsg(String agentMessageJSON) {

        AgentMessageDTO messageDTO;

        // 1. JSON解析
        try {
            messageDTO = JSON.parseObject(agentMessageJSON,  AgentMessageDTO.class);
        } catch (Exception e) {
            log.error("Agent消息JSON解析失败: {}",  agentMessageJSON,  e);
            // 这里是否抛出异常取决于MQ重试机制
            throw e;
        }
        log.info(
                "收到Agent事件: taskId={}, runId={}, agent={}, event={}, step={}, status={}",
                messageDTO.getTaskId(),
                messageDTO.getRunId(),
                messageDTO.getAgentName(),
                messageDTO.getEvent(),
                messageDTO.getStep(),
                messageDTO.getStatus()
        );
        // 2. Event + Task 状态必须同事务
        try {
            transactionTemplate.executeWithoutResult(status -> {
                // 历史事件
                agentEventService.insertAgentEvent(messageDTO);
                // 当前 Task 状态
                agentTaskService.updateTaskStatus(messageDTO);
            });
        } catch (Exception e) {
            log.error(
                    "Agent事件持久化失败: taskId={}, runId={}, messageId={}",
                    messageDTO.getTaskId(),
                    messageDTO.getRunId(),
                    messageDTO.getMessageId(),
                    e
            );
            // 让MQ消费框架知道这次消费失败
            throw e;
        }
        // 3. TODO 事务成功提交之后，再推给前端
        log.debug(
                "Agent事件持久化成功: taskId={}, runId={}, event={}",
                messageDTO.getTaskId(),
                messageDTO.getRunId(),
                messageDTO.getEvent()
        );
    }
}