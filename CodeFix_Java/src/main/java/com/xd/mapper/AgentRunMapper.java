package com.xd.mapper;

import com.xd.model.entity.AgentRunDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentRunMapper {
    void insertAgentRun(AgentRunDO run);

    Integer selectMaxAttemptByTaskId(@Param("taskId") String taskId);

    List<AgentRunDO> selectByTaskId(String taskId);

    void updateRun(AgentRunDO update);

    AgentRunDO selectByTaskIdAndRunId(
            @Param("taskId") String taskId,
            @Param("runId") String runId
    );
}
