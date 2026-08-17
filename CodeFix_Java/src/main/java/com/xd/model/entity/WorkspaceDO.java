package com.xd.model.entity;

import lombok.Data;

@Data
public class WorkspaceDO {

    private Long id;

    private String workspaceId;
    private String sessionId;

    private String name;

    private String rootPath;
    private String status;

    private Long createdAt;

    private Long updatedAt;
}
