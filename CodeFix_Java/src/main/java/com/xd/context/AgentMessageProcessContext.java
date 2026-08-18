package com.xd.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentMessageProcessContext {

    private Long eventId;

    private String diffId;
}