package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkspaceFileVO {

    private String workspaceId;

    /**
     * Workspace 内相对路径
     */
    private String filePath;

    private String content;
}