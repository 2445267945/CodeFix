package com.xd.model.vo;


import lombok.Data;

import java.util.List;

/**
 * Agent Chat 展示模型
 *
 * 对应前端：
 * AgentChatViewModel
 *
 * 数据来源：
 * agent_chat_message
 * + agent_event
 * + task_result
 * + workspace
 *
 * 由 AgentChatAssembler 负责组装。
 */
@Data
public class AgentChatViewVO {
    private String sessionId;
    private String taskId;
    private String runId;
    private String workspaceId;
    private String workspaceName;
    private String rootPath;
    private List<AgentChatTurnVO> turns;

    @Data
    public static class UserMessageVO {

        private String messageId;

        private String content;

        private Long timestamp;
    }

    @Data
    public static class AgentMessageVO {

        private String agentName;

        private List<AgentChatBlockVO> blocks;

        private FinalAnswerVO finalAnswer;
    }

    @Data
    public static class FinalAnswerVO {

        private String type;

        private String id;

        private String content;

        private Long timestamp;
    }
}
