package com.xd.controller;

import com.xd.controller.response.Result;
import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.vo.WorkspaceFileVO;
import com.xd.model.vo.WorkspaceSessionsVO;
import com.xd.model.vo.WorkspaceTreeNodeVO;
import com.xd.model.vo.WorkspaceTreeVO;
import com.xd.model.vo.WorkspaceVO;
import com.xd.service.WorkspaceFileService;
import com.xd.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/agent/workspace")
public class WorkspaceController {

    @Autowired
    private WorkspaceFileService workspaceFileService;
    @Autowired
    private WorkspaceService workspaceService;

    @GetMapping("/{workspaceId}/file")
    public Result<WorkspaceFileVO> getFile(@PathVariable String workspaceId, @RequestParam("path") String filePath) {
        WorkspaceFileVO file = workspaceFileService.getFile(workspaceId, filePath);
        return Result.success(file);
    }

    @GetMapping("/{workspaceId}/tree")
    public Result<WorkspaceTreeVO> getTree(@PathVariable String workspaceId) {
        WorkspaceTreeVO tree = workspaceFileService.getTree(workspaceId);
        return Result.success(tree);
    }

    @GetMapping("/tree")
    public Result<WorkspaceTreeVO> getTreeByPath(@RequestParam("path") String workspacePath) {
        WorkspaceTreeVO tree = workspaceFileService.getTreeByPath(workspacePath);
        return Result.success(tree);
    }

    /**
     * 懒加载：查询某个目录下的直接子节点。
     * path 为空时等价于查询根目录第一层。
     */
    @GetMapping("/{workspaceId}/children")
    public Result<List<WorkspaceTreeNodeVO>> getChildren(@PathVariable String workspaceId,
                                                         @RequestParam(value = "path", required = false) String dirPath) {
        List<WorkspaceTreeNodeVO> children = workspaceFileService.getChildren(workspaceId, dirPath);
        return Result.success(children);
    }

    /**
     * 懒加载：按 Workspace 绝对路径查询某个目录下的直接子节点。
     * path 为空时等价于查询根目录第一层。
     */
    @GetMapping("/children")
    public Result<List<WorkspaceTreeNodeVO>> getChildrenByPath(@RequestParam("workspacePath") String workspacePath,
                                                               @RequestParam(value = "path", required = false) String dirPath) {
        List<WorkspaceTreeNodeVO> children = workspaceFileService.getChildrenByPath(workspacePath, dirPath);
        return Result.success(children);
    }

    @GetMapping("/file")
    public Result<WorkspaceFileVO> getFileByPath(@RequestParam("workspacePath") String workspacePath, @RequestParam("path") String filePath) {
        WorkspaceFileVO file = workspaceFileService.getFileByPath(workspacePath, filePath);
        return Result.success(file);
    }

    @PutMapping("/{workspaceId}/file")
    public Result<Boolean> updateFile(@PathVariable String workspaceId, @RequestBody WorkspaceFileUpdateDTO request) {
        workspaceFileService.updateFile(workspaceId, request);
        return Result.success();
    }

    @GetMapping
    public Result<List<WorkspaceVO>> listWorkspaces() {
        List<WorkspaceVO> workspaceVOS = workspaceService.listWorkspaces();
        return Result.success(workspaceVOS);
    }

    /**
     * 查询所有 Workspace 及其关联的 Session（用于按项目维度展示会话）
     */
    @GetMapping("/sessions")
    public Result<List<WorkspaceSessionsVO>> listWorkspacesWithSessions() {
        List<WorkspaceSessionsVO> result = workspaceService.listWorkspacesWithSessions();
        return Result.success(result);
    }

}
