package com.xd.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentChatPhaseVO {

    /**
     * Phase 唯一 ID
     *
     * 每次进入一个新的工作阶段都创建新的 ID。
     */
    private String id;

    /**
     * Phase 类型
     *
     * ANALYSIS
     * IMPLEMENTATION
     * VERIFICATION
     * SUBTASK
     * ERROR
     */
    private String type;

    /**
     * 面向用户展示的标题
     *
     * 例如：
     * 分析问题
     * 修改代码
     * 验证修改
     */
    private String title;

    /**
     * Phase 状态
     *
     * running
     * waiting
     * completed
     * failed
     */
    private String status;

    /**
     * 当前 Phase 中的 Activity。
     */
    private List<AgentChatBlockVO> activities = new ArrayList<>();

    /**
     * Phase 开始时间。
     */
    private Long startTime;

    /**
     * Phase 最后活动时间。
     */
    private Long endTime;
}