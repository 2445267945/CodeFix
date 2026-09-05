package com.xd.service;

import com.xd.model.dto.AgentHeartbeatDTO;

public interface AgentHeartbeatHandlerService {
    boolean updateHeartbeat(AgentHeartbeatDTO heartbeat);
}
