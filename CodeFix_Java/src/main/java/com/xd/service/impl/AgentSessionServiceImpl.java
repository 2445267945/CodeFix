package com.xd.service.impl;

import com.xd.exception.BusinessException;
import com.xd.mapper.AgentSessionMapper;
import com.xd.mapper.AgentTaskMapper;
import com.xd.model.entity.AgentSessionDO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.model.vo.AgentSessionVO;
import com.xd.model.vo.TaskDetailVO;
import com.xd.service.AgentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentSessionServiceImpl implements AgentSessionService {

    @Autowired
    private AgentSessionMapper agentSessionMapper;
    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Override
    public AgentSessionDO createSession(String question) {

        long now = System.currentTimeMillis();

        AgentSessionDO session = new AgentSessionDO();

        session.setSessionId(UUID.randomUUID().toString());

        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setTitle(question);
        agentSessionMapper.insertSession(session);

        return session;
    }

    @Override
    public AgentSessionDO getSessionById(String sessionId) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId不能为空");
        }

        AgentSessionDO session = agentSessionMapper.selectBySessionId(sessionId);

        if (session == null) {
            throw new BusinessException("Session不存在: " + sessionId);
        }

        return session;
    }

    @Override
    public List<AgentSessionDO> getSessions() {
        return agentSessionMapper.selectSessions();
    }

    @Override
    public AgentSessionDO getOrCreateSession(String sessionId, String question) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession(question);
        }
        AgentSessionDO session = agentSessionMapper.selectBySessionId(sessionId);
        if (session != null) {
            return session;
        }
        // 防止调用方传入一个不存在的 sessionId
        throw new BusinessException("Session不存在: " + sessionId);
    }

    @Override
    public void bindWorkspace(String sessionId, String workspaceId) {

        AgentSessionDO update = new AgentSessionDO();

        update.setSessionId(sessionId);
        update.setWorkspaceId(workspaceId);
        update.setUpdatedAt(System.currentTimeMillis());

        agentSessionMapper.updateSession(update);
    }
}