package com.xd.model.dto;

import lombok.Data;

import lombok.Data;

@Data
public class ChatMessageCreateDTO {

    private String content;
    private String sessionId;
    private String workspaceName;
}