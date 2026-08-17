package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentSessionDO {

    private Long id;

    private String sessionId;

    /**
     * 第一版暂时允许为空
     */
    private String workspaceId;
    /**
     * 用户第一次发送的内容,临时的作为title
     */
    private String title;

    private Long createdAt;

    private Long updatedAt;
}