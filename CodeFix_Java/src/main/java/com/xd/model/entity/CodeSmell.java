package com.xd.model.entity;

import lombok.Data;

@Data
public class CodeSmell {

    /**
     * 嫌疑代码行号
     */
    private Integer lineNumber;

    /**
     * 问题类型（如 N+1_QUERY, TRANSACTION_MISUSE）
     */
    private String type;

    /**
     * 嫌疑代码片段
     */
    private String codeSnippet;

    /**
     * 问题描述
     */
    private String description;
}