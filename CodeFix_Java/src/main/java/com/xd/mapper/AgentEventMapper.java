package com.xd.mapper;

import com.xd.model.entity.AgentEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentEventMapper {

    int insertAgentEvent(AgentEventDO agentEventDO);

    List<AgentEventDO> selectByTaskId(String taskId);

    List<AgentEventDO> selectByTaskIdAndRunId(String taskId, String runId);

}
