package com.xd.service.impl;

import com.xd.exception.BusinessException;
import com.xd.mapper.WorkSpaceMapper;
import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.entity.AgentSessionDO;
import com.xd.model.entity.WorkspaceDO;
import com.xd.model.vo.AgentSessionVO;
import com.xd.model.vo.WorkspaceVO;
import com.xd.service.AgentSessionService;
import com.xd.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final String WORKSPACE_ROOT = "/data/workspaces";

    @Autowired
    private WorkSpaceMapper workspaceMapper;
    @Autowired
    private AgentSessionService agentSessionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkspaceDO createWorkspace(WorkspaceFileUpdateDTO request) {
        if (request == null || !StringUtils.hasText(request.getPath())) {
            throw new BusinessException("Workspace路径不能为空");
        }
        String rootPath = request.getPath().trim();
        Path path = Paths.get(rootPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new BusinessException("Workspace目录不存在: " + rootPath);
        }
        if (!Files.isDirectory(path)) {
            throw new BusinessException("Workspace路径不是目录: " + rootPath);
        }
        long now = System.currentTimeMillis();
        String workspaceId = UUID.randomUUID().toString();
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setWorkspaceId(workspaceId);
        workspace.setName(name);
        workspace.setRootPath(path.toString());
        workspace.setStatus("READY");
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceMapper.insertWorkspace(workspace);
        return workspace;
    }

    @Override
    public WorkspaceDO getWorkspace(String workspaceId) {

        WorkspaceDO workspace = workspaceMapper.selectByWorkspaceId(workspaceId);

        if (workspace == null) {
            throw new IllegalArgumentException("Workspace不存在: " + workspaceId);
        }

        return workspace;
    }

    @Override
    public List<WorkspaceVO> listWorkspaces() {
        List<WorkspaceDO> workspaces = workspaceMapper.selectAllWorkspaces();
        List<WorkspaceVO> result = new ArrayList<>();
        for (WorkspaceDO workspace : workspaces) {
            WorkspaceVO vo = WorkspaceVO.builder()
                    .workspaceId(workspace.getWorkspaceId())
                    .name(workspace.getName())
                    .status(workspace.getStatus())
                    .createdAt(workspace.getCreatedAt())
                    .updatedAt(workspace.getUpdatedAt())
                    .build();
            result.add(vo);
        }
        return result;
    }

//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public WorkspaceDO getOrCreateWorkspace(String sessionId) {
//        WorkspaceDO existing = workspaceMapper.selectBySessionId(sessionId);
//        if (existing != null) {
//            return existing;
//        }
//        return createWorkspace(sessionId, "Workspace-" + sessionId.substring(0, 8));
//    }

    @Override
    public void initializeFile(String workspaceId, String fileName, String code) {

        WorkspaceDO workspace = getWorkspace(workspaceId);

        if (!"READY".equals(workspace.getStatus())) {
            throw new IllegalStateException("Workspace当前不可用: " + workspaceId);
        }

        if (fileName == null || fileName.isBlank()) {

            throw new IllegalArgumentException("fileName不能为空");
        }

        if (code == null) {
            code = "";
        }

        try {

            Path root = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();

            Path target = root.resolve(fileName).normalize();

            /*
             * 防止 ../ 越界写文件
             */
            if (!target.startsWith(root)) {
                throw new IllegalArgumentException("非法文件路径: " + fileName);
            }

            Path parent = target.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(target, code, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);


        } catch (IOException e) {

            log.error("初始化Workspace文件失败: workspaceId={}, fileName={}", workspaceId, fileName, e);

            throw new BusinessException("Workspace文件初始化失败", e);
        }
    }

    @Override
    public WorkspaceVO getWorkspaceBySessionId(String sessionId) {
        AgentSessionVO session = agentSessionService.getSessionById(sessionId);
        if (session == null) {
            return null;
        }
        String workspaceId = session.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            return null;
        }
        WorkspaceDO workspaceDO = workspaceMapper.selectByWorkspaceId(workspaceId);
        if (workspaceDO == null) {
            return null;
        }
        return WorkspaceVO.builder()
                .workspaceId(workspaceDO.getWorkspaceId())
                .name(workspaceDO.getName())
                .status(workspaceDO.getStatus())
                .createdAt(workspaceDO.getCreatedAt())
                .updatedAt(workspaceDO.getUpdatedAt())
                .build();
    }

    @Override
    public WorkspaceDO getWorkspaceByRootPath(String workspacePath) {
        if (!StringUtils.hasText(workspacePath)) {
            return null;
        }

        String rootPath = Paths.get(workspacePath.trim())
                .toAbsolutePath()
                .normalize()
                .toString();

        return workspaceMapper.selectWorkspaceByRootPath(rootPath);
    }
}