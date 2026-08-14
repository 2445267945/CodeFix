package com.xd.model.dto;

import com.xd.mq.message.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentTaskMessage extends BaseMessage {

    /**
     * 给 Agent 的完整指令
     */
    private String question;

    /**
     * 原始代码（便于 Python/工具直接用，不必再从 question 里抠）
     */
    private String code;

    /**
     * 预扫描结果
     * 没有则可空
     */
    private List<CodeSmellDTO> smells;

    private String command;
    private String runId;

    /**
     * 扩展位：优先级、超时、回调 topic 等，不破坏老字段
     */
    private Map<String, Object> extras;
}