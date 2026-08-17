package com.xd.mapper;

import com.xd.model.entity.AgentSessionDO;
import com.xd.model.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentSessionMapper {

    int insertSession(AgentSessionDO session);

    AgentSessionDO selectBySessionId(String sessionId);

    List<AgentSessionDO> selectSessions();

    List<ChatMessageDO> selectByTaskId(String taskId);

    int updateSession(AgentSessionDO session);
}