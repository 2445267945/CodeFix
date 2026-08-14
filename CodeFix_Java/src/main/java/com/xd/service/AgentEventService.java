package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import org.springframework.stereotype.Service;

public interface AgentEventService {
    public void insertAgentEvent(AgentMessageDTO agentMessageDTO);
}
