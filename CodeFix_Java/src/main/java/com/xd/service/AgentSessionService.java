package com.xd.service;

import com.xd.model.entity.AgentSessionDO;

import java.util.List;

public interface AgentSessionService {

    /**
     * 创建一个新的 Session
     */
    AgentSessionDO createSession(String question);

    /**
     * 根据 sessionId 查询
     */
    AgentSessionDO getSessionById(String sessionId);


    List<AgentSessionDO> getSessions();

    /**
     * 创建 Session，如果已有则直接返回
     */
    AgentSessionDO getOrCreateSession(String sessionId, String question);

    void bindWorkspace(String sessionId, String workspaceId);

}