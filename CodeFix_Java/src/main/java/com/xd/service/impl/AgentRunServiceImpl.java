package com.xd.service.impl;

import com.xd.mapper.AgentRunMapper;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.enums.AuditTaskStatusEnum;
import com.xd.service.AgentRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentRunServiceImpl implements AgentRunService {

    @Autowired
    private AgentRunMapper agentRunMapper;

    @Override
    public AgentRunDO createFirstRun(String runId, String taskId, String sessionId) {
        long now = System.currentTimeMillis();
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(sessionId);
        run.setAttempt(1);
        run.setStatus(AuditTaskStatusEnum.CREATED.statusCode);
        run.setCreatedAt(now);
        run.setStartedAt(now);

        agentRunMapper.insertAgentRun(run);

        return run;
    }
}
