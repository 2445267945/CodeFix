package com.xd.model.entity;

import lombok.Data;

@Data
public class CodeIssue {

    /**
     * 问题所在行号
     */
    private Integer lineNumber;

    /**
     * 问题类型（如：N+1_QUERY, NULL_POINTER, TRANSACTION_MISUSE...）
     */
    private String type;

    /**
     * 严重程度：HIGH / MEDIUM / LOW
     */
    private String severity;

    /**
     * 问题描述（中文，如“在循环中执行单条SQL查询，存在N+1性能风险”）
     */
    private String description;

    /**
     * 具体的代码片段（用于前端高亮）
     */
    private String codeSnippet;

    /**
     * 修复建议（纯文本）
     */
    private String suggestion;

    /**
     * 修复后的代码片段（仅针对该行）
     * 如果AI只修改了这一行，这里放新代码；如果整体重构，此字段可为空
     */
    private String fixedSnippet;
}
