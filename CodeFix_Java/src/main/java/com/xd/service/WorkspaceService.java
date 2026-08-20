package com.xd.service;

import com.xd.model.entity.WorkspaceDO;
import com.xd.model.vo.WorkspaceVO;

import java.util.List;

public interface WorkspaceService {

    /**
     * 创建 Workspace
     */
    WorkspaceDO createWorkspace(String name);

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
//    WorkspaceDO getOrCreateWorkspace(String sessionId);

    /**
     * 获取所有项目
     */
    List<WorkspaceVO> listWorkspaces();
    /**
     * 初始化文件到 Workspace
     */
    void initializeFile(String workspaceId, String fileName, String code);

    WorkspaceVO getWorkspaceBySessionId(String sessionId);
}