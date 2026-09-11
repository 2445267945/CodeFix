package com.xd.service.impl;

import com.xd.service.TaskMemoryAsyncService;
import com.xd.service.TaskMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class TaskMemoryAsyncServiceImpl implements TaskMemoryAsyncService {

    @Autowired
    private TaskMemoryService taskMemoryService;

    @Async("asyncExecutor")
    public void save(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        taskMemoryService.save(taskId);
    }
}
