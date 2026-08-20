package com.xd.controller;

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
@RequestMapping("/api/audit/workspace")
public class WorkspaceController {

    @Autowired
    private WorkspaceFileService workspaceFileService;
    @Autowired
    private WorkspaceService workspaceService;

    @GetMapping("/{workspaceId}/file")
    public WorkspaceFileVO getFile(@PathVariable String workspaceId, @RequestParam("path") String filePath) {
        return workspaceFileService.getFile(workspaceId, filePath);
    }

    @GetMapping("/{workspaceId}/tree")
    public WorkspaceTreeVO getTree(@PathVariable String workspaceId) {
        return workspaceFileService.getTree(workspaceId);
    }

    @PutMapping("/{workspaceId}/file")
    public void updateFile(@PathVariable String workspaceId, @RequestBody WorkspaceFileUpdateDTO request) {
        workspaceFileService.updateFile(workspaceId, request);
    }

    @GetMapping
    public List<WorkspaceVO> listWorkspaces() {
        return workspaceService.listWorkspaces();
    }

}
