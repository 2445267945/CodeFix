package com.xd.model.entity;

import lombok.Data;

@Data
public class ChatMessageDO {

    private Long id;

    private String messageId;

    private String taskId;

    private String sessionId;

    private String runId;

    private String role;

    private String content;

    private Long createdAt;
}