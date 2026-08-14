package com.xd.mapper;

import com.xd.model.entity.AgentRunDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentRunMapper {
    void insertAgentRun(AgentRunDO run);

    Integer selectMaxAttemptByTaskId(@Param("taskId") String taskId);
}
