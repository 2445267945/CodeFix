package com.xd.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.xd.mq.message.BaseMessage;
import lombok.Data;

import java.util.Map;

@Data
public class AgentMessageDTO extends BaseMessage {
    // ===== Java → Python 时填充 =====
    private String question;      // 用户问题/指令
    private String code;          // 待审计的代码

    // ===== Python → Java 时填充 =====
    private String agentName;     // 哪个agent
    private String parentAgent;   // 谁调用了我
    private String status;        // THINKING / COMPLETED / FAILED
    private String thought;       // 当前思考
    private Map<String,Object> output;     // 最终结果
}
