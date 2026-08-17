package com.xd.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentRunVO {

    private String runId;

    private String taskId;

    private String sessionId;

    /**
     * 第几次执行
     */
    private Integer attempt;

    /**
     * 数据库中的状态码
     */
    private Integer status;

    /**
     * 状态英文描述
     */
    private String statusValue;

    private Long startedAt;

    private Long endedAt;

    private String errorMessage;

    private Long createdAt;

    /**
     * 是否当前Run
     */
    private Boolean current;
}
