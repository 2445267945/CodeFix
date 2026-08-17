package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatStreamVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentSseService {
    /**
     * 建立指定 Task 的 SSE 连接
     */
    SseEmitter connect(String taskId);

    /**
     * 向指定 Task 的前端连接推送 Agent Event
     */
    void send(AgentChatStreamVO agentChatStreamVO);
}
