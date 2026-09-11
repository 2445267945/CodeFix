package com.xd.service;


import com.xd.model.dto.TaskMemorySearchResultDTO;
import com.xd.model.entity.TaskMemoryDO;

import java.util.List;

/**
 * Agent Context 检索服务。
 *
 * 该服务只负责“检索哪些历史 Task”，不负责最终 SessionContext 的组装。
 */
public interface ContextRetrievalService {

    /**
     * 检索与当前问题相关的历史 Task。
     *
     * @param workspaceId 当前 Workspace ID
     * @param question    当前用户问题
     * @param topK        最大召回数量
     * @return 相关 Task Memory
     */
    List<TaskMemorySearchResultDTO> retrieve(String workspaceId, String question, int topK);
}