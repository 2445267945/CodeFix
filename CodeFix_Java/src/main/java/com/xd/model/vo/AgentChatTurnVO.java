package com.xd.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentChatTurnVO {

    /**
     * Turn 唯一 ID
     *
     * 第一阶段可以直接使用 Run ID，
     * 后续如果数据库增加 turnId 再切换。
     */
    private String taskId;

    /**
     * 当前 Turn 对应的 Run
     *
     * 用于前端知道这一轮正在执行哪一次 Run。
     */
    private String runId;

    /**
     * 用户消息
     */
    private AgentChatViewVO.UserMessageVO user;

    /**
     * Agent 内容
     */
    private AgentChatViewVO.AgentMessageVO agent;
    private List<AgentChatPhaseVO> phases = new ArrayList<>();
}