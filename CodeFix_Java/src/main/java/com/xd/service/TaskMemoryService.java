package com.xd.service;

import com.xd.model.entity.TaskMemoryDO;

public interface TaskMemoryService {

    TaskMemoryDO buildFromTask(String taskId);

    void save(String taskId);

    void rebuild(String taskId);

    TaskMemoryDO get(String taskId);

    void delete(String taskId);
}
