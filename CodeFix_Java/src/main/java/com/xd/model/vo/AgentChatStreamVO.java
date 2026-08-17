package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentChatStreamVO {

    private String taskId;

    private String runId;

    /**
     * 当前 SSE 消息 ID
     */
    private String messageId;
    private String sessionId;
    /**
     * BLOCK_APPEND
     * BLOCK_UPDATE
     * RESULT_REFRESH
     */
    private String type;

    /**
     * Block 数据
     */
    private AgentChatBlockVO block;

    /**
     * 是否要求前端重新拉取完整 Result
     */
    private Boolean refreshResult;

    /**
     * 当前事件时间
     */
    private Long timestamp;
}