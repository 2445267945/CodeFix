package com.xd.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentChatPhaseVO {

    /**
     * Agent 一次工作说明
     *
     * 对应一次 narration + 后续 actions
     */
    private String id;

    /**
     * Agent 面向用户的说明
     */
    private String summary;

    /**
     * 当前 Step 状态
     */
    private String status;

    private Long startTime;

    private Long endTime;

    private List<AgentChatBlockVO> activities = new ArrayList<>();
}