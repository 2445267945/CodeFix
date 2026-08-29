package com.xd.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.mapper.AgentEventMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.vo.AgentEventVO;
import com.xd.service.AgentEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentEventServiceImpl implements AgentEventService {

    @Autowired
    private AgentEventMapper agentEventMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AgentEventDO insertAgentEvent(AgentMessageDTO dto) {
        AgentEventDO event = new AgentEventDO();
        event.setMessageId(dto.getMessageId());
        event.setTaskId(dto.getTaskId());
        event.setSessionId(dto.getSessionId());
        event.setAgentName(dto.getAgentName());
        event.setParentAgent(dto.getParentAgent());
        event.setEvent(dto.getEvent());
        event.setRunId(dto.getRunId());
        event.setStep(dto.getStep());
        event.setActionId(dto.getActionId());
        event.setStatus(dto.getStatus());
        try {
            event.setOutput(objectMapper.writeValueAsString(dto.getOutput()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        event.setEventTimestamp(dto.getTimestamp());

        try {
            agentEventMapper.insertAgentEvent(event);
        } catch (DuplicateKeyException e) {
            log.info("Agent Event 重复消息，忽略: taskId={}, runId={}, messageId={}", event.getTaskId(), event.getRunId(), event.getMessageId());
            return null;
        }
        return event;
    }

    @Override
    public List<AgentEventDO> getEvents(String taskId, String runId) {
        return agentEventMapper.selectByTaskIdAndRunId(taskId, runId);
    }

    @Override
    public AgentEventDO getToolWaitingByActionId(String taskId, String runId, String actionId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        if (runId == null || runId.isBlank()) {
            return null;
        }
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        return agentEventMapper.selectToolWaitingByActionId(taskId, runId, actionId);
    }
}
