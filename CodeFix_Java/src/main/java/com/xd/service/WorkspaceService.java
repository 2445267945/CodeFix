package com.xd.service;

import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.entity.WorkspaceDO;
import com.xd.model.vo.WorkspaceSessionsVO;
import com.xd.model.vo.WorkspaceVO;

import java.util.List;

public interface WorkspaceService {

    /**
     * 创建 Workspace
     */
    WorkspaceDO createWorkspace(WorkspaceFileUpdateDTO request);

    /**
     * 查询 Workspace
     */
    WorkspaceDO getWorkspace(String workspaceId);

    /**
     * 获取所有项目
     */
    List<WorkspaceVO> listWorkspaces();

    /**
     * 查询所有 Workspace 及其关联的 Session（用于按项目维度展示会话）
     */
    List<WorkspaceSessionsVO> listWorkspacesWithSessions();
    /**
     * 初始化文件到 Workspace
     */
    void initializeFile(String workspaceId, String fileName, String code);

    WorkspaceVO getWorkspaceBySessionId(String sessionId);

    WorkspaceDO getWorkspaceByRootPath(String workspacePath);
}