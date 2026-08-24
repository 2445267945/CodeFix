package com.xd.state;

import com.xd.model.enums.AgentTaskStatusEnum;
import org.springframework.stereotype.Component;

@Component
public class AgentTransitionGuard {

    public boolean canApprove(AgentTransitionContext context, String requestActionId) {
        if (context == null || context.getState() == null) {
            return false;
        }

        if (context.getState() != AgentTaskStatusEnum.WAITING_HUMAN) {
            return false;
        }

        String pendingActionId = context.getActionId();

        return pendingActionId != null
                && !pendingActionId.isBlank()
                && requestActionId != null
                && !requestActionId.isBlank()
                && pendingActionId.equals(requestActionId);
    }

    public boolean canReject(
            AgentTransitionContext context,
            String requestActionId
    ) {
        if (context == null || context.getState() == null) {
            return false;
        }

        if (context.getState() != AgentTaskStatusEnum.WAITING_HUMAN) {
            return false;
        }

        String pendingActionId = context.getActionId();

        return pendingActionId != null
                && !pendingActionId.isBlank()
                && requestActionId != null
                && !requestActionId.isBlank()
                && pendingActionId.equals(requestActionId);
    }

    public boolean canCancel(AgentTransitionContext context) {
        if (context == null || context.getState() == null) {
            return false;
        }

        return switch (context.getState()) {
            case CREATED, QUEUED, AGENT_THINKING, TOOL_CALLING, WAITING_HUMAN -> true;
            case NEED_RETRY, COMPLETED, CANCELLED -> false;
        };
    }

    public boolean canResume(AgentTransitionContext context) {
        if (context == null || context.getState() == null) {
            return false;
        }
        AgentTaskStatusEnum state = context.getState();
        if (state != AgentTaskStatusEnum.CANCELLED && state != AgentTaskStatusEnum.WAITING_HUMAN) {
            return false;
        }
        return context.getCheckpointId() != null && !context.getCheckpointId().isBlank();
    }
}
