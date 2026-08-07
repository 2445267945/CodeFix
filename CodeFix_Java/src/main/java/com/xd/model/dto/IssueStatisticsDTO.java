package com.xd.model.dto;

import lombok.Data;

/**
 * 问题统计信息
 */
@Data
public class IssueStatisticsDTO {

    /**
     * 高危问题数量
     */
    private Integer highCount;

    /**
     * 中危问题数量
     */
    private Integer mediumCount;

    /**
     * 低危问题数量
     */
    private Integer lowCount;

    /**
     * 问题总数
     */
    private Integer totalCount;
}