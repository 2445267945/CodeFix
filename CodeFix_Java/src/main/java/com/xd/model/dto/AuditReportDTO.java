package com.xd.model.dto;


import lombok.Data;
import java.util.List;

/**
 * 审计报告 - 核心领域实体
 * 由 Python Agent 生成，Java 端二次校验后返回给前端
 */
@Data
public class AuditReportDTO {
    private String taskId;
    /**
     * 状态 Python 端对齐
     */
    private String status;
    private String statusCode;
    /**
     * 整体健康度评分（0-100）
     */
    private Integer healthScore;

    /**
     * 发现的所有问题列表
     */
    private List<CodeIssueDTO> issues;

    /**
     * AI 修复后的完整代码（可能为 null）
     */
    private String fixedCode;

    /**
     * 修复摘要（中文，供前端展示）
     */
    private String summary;

    /**
     * 元数据（调试/追踪用，不返回给前端）
     * 这个字段是 AuditReport 独有，AuditResponse 不需要
     */
    private ReportMetadataDTO metadata;
}
