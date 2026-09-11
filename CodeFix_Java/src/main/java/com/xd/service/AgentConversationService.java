package com.xd.service;


import com.xd.model.context.SessionContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.ChatMessageCreateDTO;
import com.xd.model.vo.ChatMessageVO;
import com.xd.model.vo.SessionVO;

import java.util.List;

public interface AgentConversationService {

    ChatMessageVO sendMessage(ChatMessageCreateDTO request);

    List<ChatMessageVO> getMessages(String sessionId);

    List<SessionVO> getMessages();

    void saveAssistantMessage(AgentMessageDTO messageDTO);

    SessionContext buildSessionContext(String sessionId, String question);
}
