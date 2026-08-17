package com.xd.model.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SessionContext {

    /**
     * Session 中的历史对话
     */
    private List<ChatMessageContext> messages;
}