package com.xd.service;

import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.vo.WorkspaceFileVO;
import com.xd.model.vo.WorkspaceTreeNodeVO;
import com.xd.model.vo.WorkspaceTreeVO;

import java.util.List;

public interface WorkspaceFileService {
    WorkspaceFileVO getFile(String workspaceId, String filePath);

    /**
     * 仅返回 Workspace 根目录下的第一层节点（不再递归展开）。
     */
    WorkspaceTreeVO getTree(String workspaceId);

    void updateFile(String workspaceId, WorkspaceFileUpdateDTO request);

    /**
     * 仅返回指定路径根目录下的第一层节点（不再递归展开）。
     */
    WorkspaceTreeVO getTreeByPath(String workspacePath);

    WorkspaceFileVO getFileByPath(String workspacePath, String filePath);

    /**
     * 懒加载：查询某个目录下的直接子节点。
     *
     * @param dirPath 相对于 Workspace 根目录的目录路径，空表示根目录
     */
    List<WorkspaceTreeNodeVO> getChildren(String workspaceId, String dirPath);

    /**
     * 懒加载：按 Workspace 绝对路径查询某个目录下的直接子节点。
     *
     * @param dirPath 相对于 Workspace 根目录的目录路径，空表示根目录
     */
    List<WorkspaceTreeNodeVO> getChildrenByPath(String workspacePath, String dirPath);
}
