package com.xd.service.impl;

import com.xd.mapper.WorkSpaceMapper;
import com.xd.model.dto.WorkspaceFileUpdateDTO;
import com.xd.model.entity.WorkspaceDO;
import com.xd.model.vo.WorkspaceFileVO;
import com.xd.model.vo.WorkspaceTreeNodeVO;
import com.xd.model.vo.WorkspaceTreeVO;
import com.xd.service.WorkspaceFileService;
import com.xd.service.WorkspaceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class WorkspaceFileServiceImpl implements WorkspaceFileService {

    @Autowired
    private WorkSpaceMapper workSpaceMapper;
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public WorkspaceFileVO getFile(String workspaceId, String filePath) {

        WorkspaceDO workspace = workSpaceMapper.selectByWorkspaceId(workspaceId);

        if (workspace == null) {
            throw new RuntimeException("Workspace 不存在: " + workspaceId);
        }

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }

        Path root = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();

        Path target = root.resolve(filePath).normalize();

        /*
         * 防止：
         * ../../xxx
         *
         * 越出当前 Workspace。
         */
        if (!target.startsWith(root)) {
            throw new SecurityException("非法文件路径: " + filePath);
        }

        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new RuntimeException("文件不存在: " + filePath);
        }

        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);

            return WorkspaceFileVO.builder()
                    .workspaceId(workspaceId)
                    .filePath(filePath)
                    .content(content)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        }
    }

    @Override
    public WorkspaceTreeVO getTree(String workspaceId) {
        WorkspaceDO workspace = workspaceService.getWorkspace(workspaceId);
        if (workspace == null) {
            throw new RuntimeException("Workspace不存在: " + workspaceId);
        }
        Path rootPath = Paths.get(workspace.getRootPath());
        if (!Files.exists(rootPath)) {
            throw new RuntimeException("Workspace目录不存在: " + workspace.getRootPath());
        }
        WorkspaceTreeVO tree = new WorkspaceTreeVO();
        tree.setWorkspaceId(workspace.getWorkspaceId());
        tree.setName(workspace.getName());
        List<WorkspaceTreeNodeVO> treeNodes = buildTree(rootPath, rootPath);
        tree.setChildren(treeNodes);
        return tree;
    }

    @Override
    public void updateFile(String workspaceId, WorkspaceFileUpdateDTO request) {
        WorkspaceDO workspace = workspaceService.getWorkspace(workspaceId);
        if (workspace == null) {
            throw new RuntimeException("Workspace不存在: " + workspaceId);
        }
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new RuntimeException("文件路径不能为空");
        }
        Path rootPath = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();

        Path targetPath = rootPath.resolve(request.getPath()).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new RuntimeException("非法文件路径");
        }
        if (!Files.exists(targetPath)) {
            throw new RuntimeException("文件不存在: " + request.getPath());
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new RuntimeException("目标不是文件: " + request.getPath());
        }
        try {
            Files.writeString(targetPath, request.getContent() == null ? "" : request.getContent(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Workspace文件写入失败: workspaceId={}, path={}", workspaceId, request.getPath(), e);
            throw new RuntimeException("文件保存失败", e);
        }
    }

    private List<WorkspaceTreeNodeVO> buildTree(Path rootPath, Path currentPath) {
        List<WorkspaceTreeNodeVO> treeNodes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(currentPath)) {
            stream.forEach(file -> {
                WorkspaceTreeNodeVO node = new WorkspaceTreeNodeVO();
                node.setName(file.getFileName().toString());
                node.setPath(rootPath.relativize(file).toString().replace(File.separatorChar, '/'));
                if (Files.isDirectory(file)) {
                    node.setType("DIRECTORY");
                    node.setChildren(buildTree(rootPath, file));
                } else {
                    node.setType("FILE");
                }
                treeNodes.add(node);
            });
        } catch (IOException e) {
            throw new RuntimeException("读取 Workspace 文件树失败", e);
        }
        treeNodes.sort(Comparator.comparing(
                (WorkspaceTreeNodeVO node) -> !"DIRECTORY".equals(node.getType()))
                .thenComparing(WorkspaceTreeNodeVO::getName, String.CASE_INSENSITIVE_ORDER)
        );
        return treeNodes;
    }
}
