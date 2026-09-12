package com.xd.model.dto;

import lombok.Data;

/**
 * Workspace 与 Session 关联查询的扁平结果行。
 *
 * <p>用于一次 JOIN 查询返回「workspace + 其下 session」的全部数据，
 * 避免按 workspace 逐个查询 session 造成的 N+1 问题。</p>
 *
 * <p>当某个 workspace 下没有 session 时，session 相关字段为 null。</p>
 */
@Data
public class WorkspaceSessionRowDTO {

    /**
     * Workspace ID
     */
    private String workspaceId;

    /**
     * Workspace 名称
     */
    private String workspaceName;

    /**
     * Session ID，workspace 下无 session 时为 null
     */
    private String sessionId;

    /**
     * Session 标题
     */
    private String title;

    /**
     * Session 创建时间
     */
    private Long sessionCreatedAt;

    /**
     * Session 更新时间
     */
    private Long sessionUpdatedAt;
}
