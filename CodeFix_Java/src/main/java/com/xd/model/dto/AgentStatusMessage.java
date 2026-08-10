package com.xd.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.xd.mq.message.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentStatusMessage extends BaseMessage {

    private String agentName;
    private String parentAgent;

    /** THINKING / TOOL_CALLING / COMPLETED / ERROR */
    private String status;

    private String thought;

    /** 终态结果：code / changes / issues 等，用 JsonNode 保持灵活 */
    private JsonNode output;
}