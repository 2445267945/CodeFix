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
     * 目录的下一层子节点。
     * <p>
     * 懒加载模式下，仅当查询某个目录的子节点时才会填充该字段，
     * 顶层查询不再递归展开。
     */
    private List<WorkspaceTreeNodeVO> children;

    /**
     * 当前目录是否包含子节点。
     * <p>
     * 用于前端判断目录是否可展开（避免展开空目录时再发一次请求），
     * 对 FILE 恒为 false。
     */
    private Boolean hasChildren;
}