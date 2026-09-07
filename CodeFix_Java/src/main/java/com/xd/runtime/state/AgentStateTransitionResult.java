package com.xd.runtime.state;

import com.xd.model.enums.AgentTaskStatusEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentStateTransitionResult {

    private boolean changed;

    private AgentTaskStatusEnum fromStatus;

    private AgentTaskStatusEnum toStatus;

    private String triggerType;

    private String trigger;

    private String reason;
    private String actionId;
    private String sessionId;
}
