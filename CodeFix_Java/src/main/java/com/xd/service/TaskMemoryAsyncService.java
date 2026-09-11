package com.xd.service;

public interface TaskMemoryAsyncService {
    /**
     * 异步存taskId关联的向量
     * @param taskId
     */
    void save(String taskId);
}
