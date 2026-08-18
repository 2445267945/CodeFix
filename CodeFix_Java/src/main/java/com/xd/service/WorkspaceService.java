package com.xd.service;

import com.xd.model.entity.WorkspaceDO;

public interface WorkspaceService {

    /**
     * 创建 Workspace
     */
    WorkspaceDO createWorkspace(String sessionId, String name);

    /**
     * 查询 Workspace
     */
    WorkspaceDO getWorkspace(String workspaceId);

    /**
     * 查询 Session 对应的 Workspace
     */
    WorkspaceDO getBySessionId(String sessionId);

    /**
     * 获取 / 创建 Session 对应的 Workspace
     */
    WorkspaceDO getOrCreateWorkspace(String sessionId);

    /**
     * 初始化文件到 Workspace
     */
    void initializeFile(String workspaceId, String fileName, String code);
}