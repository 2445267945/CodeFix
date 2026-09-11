package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentSessionVO {

    private String sessionId;

    private String workspaceId;

    private String workspaceName;

    private String rootPath;

    private Long createdAt;

    private Long updatedAt;
}