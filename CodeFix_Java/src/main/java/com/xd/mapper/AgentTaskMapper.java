package com.xd.mapper;

import com.xd.model.entity.AgentTaskDO;
import com.xd.model.entity.TaskResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.checkerframework.checker.units.qual.A;

@Mapper
public interface AgentTaskMapper {

    public void updateTask(AgentTaskDO agentTaskDO);

    public void insertAgentTask(AgentTaskDO agentTaskDO);

    public AgentTaskDO selectByTaskId(String taskId);

    TaskResultDO selectTaskResult(String taskId);
}
