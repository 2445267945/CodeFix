package com.xd.mapper;

import com.xd.model.entity.AgentActionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentActionMapper {

    int insertAction(AgentActionDO action);

    AgentActionDO selectByActionId(String actionId);

    AgentActionDO selectCurrentByRunId(String runId);

    List<AgentActionDO> selectByRunId(String runId);

    int updateActionStatus(AgentActionDO action);
}
