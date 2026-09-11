package com.xd.service;

import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.vo.WorkspaceFileVO;
import com.xd.model.vo.WorkspaceTreeVO;

public interface WorkspaceFileService {
    WorkspaceFileVO getFile(String workspaceId, String filePath);

    WorkspaceTreeVO getTree(String workspaceId);

    void updateFile(String workspaceId, WorkspaceFileUpdateDTO request);

    WorkspaceTreeVO getTreeByPath(String workspacePath);

    WorkspaceFileVO getFileByPath(String workspacePath, String filePath);
}
