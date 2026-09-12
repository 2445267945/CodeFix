package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Workspace 及其关联的 Session 列表（用于按项目维度展示会话）
 */
@Data
@Builder
public class WorkspaceSessionsVO {

    private String workspaceId;

    private String workspaceName;

    private List<SessionVO> sessions;
}
