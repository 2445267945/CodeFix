package com.xd.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.xd.model.context.SessionContext;
import com.xd.mq.message.BaseMessage;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AgentMessageDTO extends BaseMessage {
    // ===== Python → Java 时填充 =====
    private String event;
    private String runId;
    private String step;
    private String agentName;     // 哪个agent
    private String parentAgent;   // 谁调用了我
    private String status;        // THINKING / COMPLETED / ERROR
    private String thought;       // 当前思考
    private Map<String,Object> output;     // 最终结果

}
