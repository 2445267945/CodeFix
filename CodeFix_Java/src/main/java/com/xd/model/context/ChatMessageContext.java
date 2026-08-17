package com.xd.model.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatMessageContext {

    private String role;

    private String content;

    private Long timestamp;
}
