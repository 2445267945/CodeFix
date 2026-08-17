package com.xd.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.mapper.AgentEventMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.vo.AgentEventVO;
import com.xd.service.AgentEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentEventServiceImpl implements AgentEventService {

    @Autowired
    private AgentEventMapper agentEventMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void insertAgentEvent(AgentMessageDTO dto) {
        AgentEventDO event = new AgentEventDO();
        event.setMessageId(dto.getMessageId());
        event.setTaskId(dto.getTaskId());
        event.setSessionId(dto.getSessionId());
        event.setAgentName(dto.getAgentName());
        event.setParentAgent(dto.getParentAgent());
        event.setEvent(dto.getEvent());
        event.setRunId(dto.getRunId());
        event.setStep(dto.getStep());
        event.setStatus(dto.getStatus());
        try {
            event.setOutput(objectMapper.writeValueAsString(dto.getOutput()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        event.setEventTimestamp(dto.getTimestamp());

        agentEventMapper.insertAgentEvent(event);
    }

    @Override
    public List<AgentEventDO> getEvents(String taskId, String runId) {
        return agentEventMapper.selectByTaskIdAndRunId(taskId, runId);
    }
}
