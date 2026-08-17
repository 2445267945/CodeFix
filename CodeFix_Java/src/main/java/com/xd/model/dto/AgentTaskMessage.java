package com.xd.model.dto;

import com.xd.model.context.SessionContext;
import com.xd.mq.message.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentTaskMessage extends BaseMessage {

    /**
     * 给 Agent 的当前任务指令
     */
    private String question;

    /**
     * 当前 Task 的代码
     */
    private String code;

    /**
     * 预扫描结果
     */
    private List<CodeSmellDTO> smells;

    /**
     * START / RESUME / RETRY / CANCEL
     */
    private String command;

    /**
     * 当前 Run
     */
    private String runId;

    /**
     * 当前workspaceId
     */
    private String workspaceId;

    /**
     * 当前 Session 历史上下文
     */
    private SessionContext sessionContext;

    /**
     * 扩展字段
     */
    private Map<String, Object> extras;
}