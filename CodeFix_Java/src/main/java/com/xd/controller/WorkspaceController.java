package com.xd.controller;

import com.xd.model.Result;
import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.vo.WorkspaceFileVO;
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

}
