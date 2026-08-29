package com.xd.runtime.state;

import com.xd.model.enums.AgentTaskStatusEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentTransitionContext {
    private String actionId;

    private String taskId;

    private String runId;

    private AgentTaskStatusEnum state;

    private Integer retryCount;

    private Integer maxRetry;

    private String checkpointId;
}