package com.xd.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Task 的可检索记忆，但是并不需要对应有一个数据库表。
 *
 * 用于将已经完成的 Task 转换成后续 Context Retrieval 可使用的记忆单元。
 * TaskMemory 本身不是原始事实数据源：
 *
 */
@Data
@Builder
public class TaskMemoryDO {

    /**
     * Task 的检索向量。
     */
    private float[] embedding;

    /**
     * 检索相关元数据。
     */
    private MetaData metaData;


    /**
     * Task Memory 元数据。
     *
     * 用于辅助 Vector Retrieval、过滤、排序以及后续上下文选择。
     */
    @Data
    @Builder
    public static class MetaData {

        /**
         * 所属 Session ID。
         */
        private String sessionId;

        /**
         * 所属 Workspace ID。
         */
        private String workspaceId;

        /**
         * Workspace 名称。
         */
        private String workspaceName;

        /**
         * Task ID。
         */
        private String taskId;

        /**
         * Task 创建时间。
         */
        private Long createdAt;

        /**
         * Task 完成时间。
         */
        private Long updatedAt;
    }
}