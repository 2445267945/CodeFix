package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageVO {

    /**
     * 本条 ChatMessage
     */
    private String messageId;

    /**
     * Session
     */
    private String sessionId;

    /**
     * Task
     *
     * 当前这一轮对话对应的 Task
     */
    private String taskId;

    /**
     * Run
     *
     * 当前这一轮正在执行的 Run
     */
    private String runId;

    /**
     * USER / ASSISTANT
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Long createdAt;
}