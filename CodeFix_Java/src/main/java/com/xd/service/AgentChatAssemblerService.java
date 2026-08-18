package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatStreamVO;
import com.xd.model.vo.AgentChatViewVO;

public interface AgentChatAssemblerService {
    AgentChatViewVO assemble(String sessionId);

    AgentChatStreamVO assemble(AgentMessageDTO agentMessageDTO);
}
