package com.xd.mq.message;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.UUID;

@Data
public class BaseMessage {
    /** 协议版本，便于以后不兼容升级 */
    private String version = "1.0";

    /** 消息创建时间 epoch ms */
    private Long timestamp = System.currentTimeMillis();

    /** 单次 MQ 消息 ID（可选，用于去重/追踪） */
    private String messageId = UUID.randomUUID().toString();

    /** 业务任务 ID：一次审计全链路共用，WS/缓存都靠它 */
    private String taskId;

    /** 会话 ID：同一用户多次审计可复用 */
    private String sessionId;

    /**
     * 路由类型：决定反序列化成哪个子类
     * 例：AGENT_TASK / AGENT_STATUS
     */
    private String type;
}