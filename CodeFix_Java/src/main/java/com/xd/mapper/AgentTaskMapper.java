package com.xd.mapper;

import com.xd.model.entity.AgentTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentTaskMapper {

    void updateTask(AgentTaskDO agentTaskDO);

    void insertAgentTask(AgentTaskDO agentTaskDO);

    AgentTaskDO selectByTaskId(String taskId);

    List<AgentTaskDO> selectTaskList();

    List<AgentTaskDO> selectBySessionId(String sessionId);
}
