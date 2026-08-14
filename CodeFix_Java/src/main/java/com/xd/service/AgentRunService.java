package com.xd.service;

import com.xd.model.entity.AgentRunDO;

public interface AgentRunService {
    public AgentRunDO createFirstRun(String runId, String taskId, String sessionId);
}
