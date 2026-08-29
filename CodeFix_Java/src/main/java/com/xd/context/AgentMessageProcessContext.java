package com.xd.context;

import com.xd.model.enums.PermissionDecisionEnum;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentMessageProcessContext {
    private String eventId;
    private String diffId;
    private PermissionDecisionEnum permissionDecision;
    private boolean duplicate;
}