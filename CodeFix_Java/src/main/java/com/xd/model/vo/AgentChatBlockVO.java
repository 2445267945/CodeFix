package com.xd.model.vo;


import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Chat 展示块
 *
 * 对应前端：
 * AgentActionBlock
 * FileChangeBlock
 * ReviewBlock
 *
 * 注意：
 * 这是展示层 VO，不是 Agent 原始 Event。
 */
@Data
public class AgentChatBlockVO {

    /**
     * Block 类型
     *
     * narration
     * action
     * file_change
     * review
     * status
     */
    private String type;

    /**
     * Block 唯一 ID
     */
    private String id;

    /**
     * Agent 名称
     *
     * action 类型主要使用。
     *
     * 子 Agent（Explorer / Fixer）产生的 Block 使用子 Agent 名称，
     * 主 Agent（Cando）产生的 Block 使用主 Agent 名称。
     */
    private String agent;

    /**
     * 父 Agent 名称
     *
     * 主 Agent 自己产生的 Block 为 null；
     * 子 Agent 产生的 Block 为调用它的父 Agent 名称。
     *
     * 前端据此把子 Agent 的 Block 归属到对应的委派（delegate）节点。
     */
    private String parentAgent;

    /**
     * 被委派的子 Agent 名称
     *
     * 仅 delegate 类型 Block 使用。
     *
     * 主 Agent（Cando）调用 run_explorer / run_fixer 时，
     * 填入被调用的子 Agent 名称（Explorer / Fixer）。
     */
    private String delegateAgent;

    /**
     * 子 Agent 产生的嵌套 Block
     *
     * 仅 delegate 类型 Block 使用。
     *
     * 子 Agent（Explorer / Fixer）执行期间产生的
     * Narration / Action / FileChange / Review / Status Block
     * 都挂在委派节点下，
     * 前端可以在委派节点内部按执行顺序展开。
     */
    private List<AgentChatBlockVO> children;

    /**
     * Action 类型
     *
     * THINK
     * READ
     * SEARCH
     * WRITE
     * EXECUTE
     * VERIFY
     * DELEGATE
     * FINISH
     * ERROR
     */
    private String action;

    /**
     * Action 状态
     *
     * running
     * completed
     * failed
     */
    private String status;

    /**
     * 面向用户的摘要
     *
     * 例如：
     * 正在读取 SortUtils.java
     * 已完成文件修改
     */
    private String summary;

    /**
     * 详细描述
     *
     * 第一版可以为空。
     */
    private String detail;

    /**
     * 文件路径
     *
     * file_change 类型使用。
     */
    private String filePath;

    /**
     * 文件操作类型
     *
     * created
     * modified
     * deleted
     * renamed
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
     * 文件修改摘要
     */
    private String changeSummary;

    /**
     * Diff ID
     *
     * 后续代码审视 / Diff 页面使用。
     */
    private String diffId;

    /**
     * Review 等级
     *
     * info
     * success
     * warning
     * error
     */
    private String level;

    /**
     * Review 标题
     */
    private String title;

    /**
     * Review 内容
     */
    private String content;

    /**
     * Review 关联文件
     */
    private List<String> relatedFiles;

    /**
     * 原始 Event ID
     *
     * 用于前端展开查看原始 Agent Event。
     */
    private List<String> sourceEventIds;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 当前 Task
     */
    private String taskId;

    /**
     * 当前 Run
     */
    private String runId;

    /**
     * Runtime Action ID
     *
     * 用户批准 / 拒绝时使用。
     */
    private String actionId;

    /**
     * 是否需要人工审批
     */
    private Boolean requiresApproval;

    private String toolName;
    private Map<String, Object> arguments;
}
