package com.xd.model.context;

import com.xd.model.entity.AgentRunDO;
import com.xd.model.entity.AgentSessionDO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.model.entity.WorkspaceDO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskRunContext {

    private AgentTaskDO task;

    private AgentRunDO run;

    private AgentSessionDO session;

    private WorkspaceDO workspace;
}