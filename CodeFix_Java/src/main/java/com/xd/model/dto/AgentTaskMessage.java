package com.xd.model.dto;

import com.xd.model.context.SessionContext;
import com.xd.model.dto.CodeSmellDTO;
import com.xd.mq.message.BaseMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentTaskMessage extends BaseMessage {

    private String question;

    private String code;

    private List<CodeSmellDTO> smells;

    /**
     * START / RESUME / RETRY / CANCEL / APPROVE / REJECT
     */
    private String command;
    private String commandType;

    /**
     * Action Command 对应的 Action ID
     */
    private String actionId;

    private String runId;

    private String workspaceId;

    private SessionContext sessionContext;

    private Map<String, Object> extras;
}