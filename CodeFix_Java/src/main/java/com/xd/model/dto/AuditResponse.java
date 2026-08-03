package com.xd.model.dto;

import com.xd.model.entity.CodeIssue;
import com.xd.model.entity.IssueStatistics;
import lombok.Data;
import java.util.List;

/**
 * 审计响应 - 对外 API 契约
 * 返回给前端或调用方的最终响应体
 */
@Data
public class AuditResponse {

    /**
     * 状态码：0=成功，1=部分成功（AI修复失败但有问题列表），-1=失败
     */
    private Integer code;

    /**
     * 提示信息（成功/失败描述）
     */
    private String message;

    /**
     * 整体健康度评分（0-100）
     * 90-100：优秀，70-89：良好，50-69：需关注，0-49：危险
     */
    private Integer healthScore;

    /**
     * 问题统计信息
     */
    private IssueStatistics statistics;

    /**
     * 发现的问题列表（用于前端渲染）
     */
    private List<CodeIssue> issues;

    /**
     * AI 修复后的完整代码（可直接复制使用）
     * 如果 status 为 partial 或 -1，此字段可能为空
     */
    private String fixedCode;

    /**
     * 修复摘要（中文描述）
     * 如："发现 3 个问题，已自动修复 2 个，1 个需人工处理"
     */
    private String summary;
}