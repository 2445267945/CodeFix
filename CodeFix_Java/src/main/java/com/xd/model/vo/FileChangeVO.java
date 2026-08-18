package com.xd.model.vo;

import lombok.Data;

/**
 * Agent 文件变更结果。
 *
 * 来源：
 * Python write_file / delete_file ToolResult
 *
 * 注意：
 * 这是 Java 内部的结构化模型，
 * 不是数据库实体。
 */
@Data
public class FileChangeVO {

    /**
     * 固定：
     * file_change
     */
    private String type;

    /**
     * Workspace 内相对路径
     */
    private String filePath;

    /**
     * created / modified / deleted / renamed
     */
    private String operation;

    /**
     * 新增行数
     */
    private Integer addedLines;

    /**
     * 删除行数
     */
    private Integer removedLines;

    /**
     * Unified Diff
     *
     * 当前阶段先保留。
     * 后续落库后改为通过 diffId 查询。
     */
    private String diff;
}