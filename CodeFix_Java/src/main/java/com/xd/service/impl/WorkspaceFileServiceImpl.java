package com.xd.service.impl;

import com.xd.exception.BusinessException;
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
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
            throw new BusinessException("Workspace 不存在: " + workspaceId);
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
            throw new BusinessException("文件不存在: " + filePath);
        }

        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);

            return WorkspaceFileVO.builder()
                    .workspaceId(workspaceId)
                    .filePath(filePath)
                    .content(content)
                    .build();

        } catch (IOException e) {
            throw new BusinessException("读取文件失败: " + filePath, e);
        }
    }

    @Override
    public WorkspaceTreeVO getTree(String workspaceId) {
        WorkspaceDO workspace = workspaceService.getWorkspace(workspaceId);
        if (workspace == null) {
            throw new BusinessException("Workspace不存在: " + workspaceId);
        }
        Path rootPath = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();
        if (!Files.exists(rootPath)) {
            throw new BusinessException("Workspace目录不存在: " + workspace.getRootPath());
        }
        if (!Files.isDirectory(rootPath)) {
            throw new BusinessException("Workspace路径不是目录: " + workspace.getRootPath());
        }
        WorkspaceTreeVO tree = new WorkspaceTreeVO();
        tree.setWorkspaceId(workspace.getWorkspaceId());
        tree.setName(workspace.getName());
        /*
         * 懒加载：
         * 只返回根目录下的第一层节点，子目录在用户展开时再按需请求。
         */
        List<WorkspaceTreeNodeVO> treeNodes = listChildren(rootPath, rootPath);
        tree.setChildren(treeNodes);
        return tree;
    }

    @Override
    public void updateFile(String workspaceId, WorkspaceFileUpdateDTO request) {
        WorkspaceDO workspace = workspaceService.getWorkspace(workspaceId);
        if (workspace == null) {
            throw new BusinessException("Workspace不存在: " + workspaceId);
        }
        if (request.getPath() == null || request.getPath().isBlank()) {
            throw new BusinessException("文件路径不能为空");
        }
        Path rootPath = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();

        Path targetPath = rootPath.resolve(request.getPath()).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException("非法文件路径");
        }
        if (!Files.exists(targetPath)) {
            throw new BusinessException("文件不存在: " + request.getPath());
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new BusinessException("目标不是文件: " + request.getPath());
        }
        try {
            Files.writeString(targetPath, request.getContent() == null ? "" : request.getContent(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Workspace文件写入失败: workspaceId={}, path={}", workspaceId, request.getPath(), e);
            throw new BusinessException("文件保存失败", e);
        }
    }

    @Override
    public WorkspaceTreeVO getTreeByPath(String workspacePath) {
        if (!StringUtils.hasText(workspacePath)) {
            throw new BusinessException("Workspace路径不能为空");
        }
        Path rootPath = Paths.get(workspacePath).toAbsolutePath().normalize();
        if (!Files.exists(rootPath)) {
            throw new BusinessException("Workspace目录不存在: " + rootPath);
        }
        if (!Files.isDirectory(rootPath)) {
            throw new BusinessException("Workspace路径不是目录: " + rootPath);
        }

        WorkspaceTreeVO tree = new WorkspaceTreeVO();
        /*
         * 临时 Workspace：
         * 尚未正式持久化，所以 workspaceId 为空。
         */
        tree.setWorkspaceId(null);
        tree.setName(rootPath.getFileName() == null ? "Default" : rootPath.getFileName().toString());
        /*
         * 懒加载：
         * 只返回根目录下的第一层节点，子目录在用户展开时再按需请求。
         */
        List<WorkspaceTreeNodeVO> treeNodes = listChildren(rootPath, rootPath);
        tree.setChildren(treeNodes);
        return tree;
    }

    @Override
    public WorkspaceFileVO getFileByPath(String workspacePath, String filePath) {
        if (!StringUtils.hasText(workspacePath)) {
            throw new BusinessException("Workspace路径不能为空");
        }
        if (!StringUtils.hasText(filePath)) {
            throw new BusinessException("文件路径不能为空");
        }
        Path root = Paths.get(workspacePath).toAbsolutePath().normalize();
        Path target = root.resolve(filePath).normalize();
        if (!target.startsWith(root)) {
            throw new SecurityException("非法文件路径: " + filePath);
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new BusinessException("文件不存在: " + filePath);
        }
        try {
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return WorkspaceFileVO.builder()
                    .filePath(filePath)
                    .content(content)
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取文件失败: " + filePath, e);
        }
    }

    @Override
    public List<WorkspaceTreeNodeVO> getChildren(String workspaceId, String dirPath) {
        WorkspaceDO workspace = workSpaceMapper.selectByWorkspaceId(workspaceId);
        if (workspace == null) {
            throw new BusinessException("Workspace 不存在: " + workspaceId);
        }
        Path rootPath = Paths.get(workspace.getRootPath()).toAbsolutePath().normalize();
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new BusinessException("Workspace目录不存在: " + workspace.getRootPath());
        }
        return listChildren(rootPath, resolveDirectory(rootPath, dirPath));
    }

    @Override
    public List<WorkspaceTreeNodeVO> getChildrenByPath(String workspacePath, String dirPath) {
        if (!StringUtils.hasText(workspacePath)) {
            throw new BusinessException("Workspace路径不能为空");
        }
        Path rootPath = Paths.get(workspacePath).toAbsolutePath().normalize();
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            throw new BusinessException("Workspace目录不存在: " + rootPath);
        }
        return listChildren(rootPath, resolveDirectory(rootPath, dirPath));
    }

    /**
     * 解析目标目录，并保证不会越出 Workspace 根目录。
     */
    private Path resolveDirectory(Path rootPath, String dirPath) {
        Path target = (dirPath == null || dirPath.isBlank())
                ? rootPath
                : rootPath.resolve(dirPath).normalize();

        if (!target.startsWith(rootPath)) {
            throw new SecurityException("非法目录路径: " + dirPath);
        }
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            throw new BusinessException("目录不存在: " + dirPath);
        }
        return target;
    }

    /**
     * 列出某个目录下的直接子节点（不递归）。
     * <p>
     * 只为目录设置 hasChildren 标记，前端据此决定是否展示可展开箭头，
     * 真正展开时再调用本方法获取下一层。
     */
    private List<WorkspaceTreeNodeVO> listChildren(Path rootPath, Path currentPath) {
        List<WorkspaceTreeNodeVO> treeNodes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(currentPath)) {
            stream.forEach(file -> {
                WorkspaceTreeNodeVO node = new WorkspaceTreeNodeVO();
                node.setName(file.getFileName().toString());
                node.setPath(rootPath.relativize(file).toString().replace(File.separatorChar, '/'));
                if (Files.isDirectory(file)) {
                    node.setType("DIRECTORY");
                    node.setHasChildren(hasChildren(file));
                } else {
                    node.setType("FILE");
                    node.setHasChildren(Boolean.FALSE);
                }
                treeNodes.add(node);
            });
        } catch (IOException e) {
            throw new BusinessException("读取 Workspace 文件树失败", e);
        }
        treeNodes.sort(Comparator.comparing(
                (WorkspaceTreeNodeVO node) -> !"DIRECTORY".equals(node.getType()))
                .thenComparing(WorkspaceTreeNodeVO::getName, String.CASE_INSENSITIVE_ORDER)
        );
        return treeNodes;
    }

    /**
     * 判断目录下是否还存在条目（仅探测第一个条目即返回，避免完整列举）。
     */
    private boolean hasChildren(Path dirPath) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            log.warn("探测目录子节点失败: {}", dirPath, e);
            return false;
        }
    }
}
