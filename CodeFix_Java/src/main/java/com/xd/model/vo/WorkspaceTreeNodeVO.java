package com.xd.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class WorkspaceTreeNodeVO {

    /**
     * 文件/目录名称
     */
    private String name;

    /**
     * 相对于 workspace 根目录的路径
     * 例如：src/main/java/App.java
     */
    private String path;

    /**
     * FILE / DIRECTORY
     */
    private String type;

    /**
     * DIRECTORY 才有子节点
     */
    private List<WorkspaceTreeNodeVO> children;
}