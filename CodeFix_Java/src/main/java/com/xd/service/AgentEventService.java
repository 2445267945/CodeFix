package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.vo.AgentEventVO;
import org.springframework.stereotype.Service;

import java.util.List;

public interface AgentEventService {
    public void insertAgentEvent(AgentMessageDTO agentMessageDTO);


    /**
     * 查询某个 Task / Run 的 Agent Event
     */
    List<AgentEventDO> getEvents(String taskId, String runId);
}
