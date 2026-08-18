package com.xd.model.entity;

import lombok.Data;

@Data
public class AgentFileChangeDO {

    private Long id;

    private String diffId;

    private String sessionId;

    private String taskId;

    private String runId;

    /**
     * 对应触发文件变更的 Agent Event
     */
    private Long eventId;

    /**
     * Workspace 内相对路径
     */
    private String filePath;

    /**
     * created / modified / deleted / renamed
     */
    private String operation;

    private Integer addedLines;

    private Integer removedLines;

    /**
     * Unified Diff
     */
    private String diffText;

    private Long createdAt;
}