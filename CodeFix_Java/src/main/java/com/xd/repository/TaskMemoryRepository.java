package com.xd.repository;

import com.xd.model.dto.TaskMemorySearchResultDTO;
import com.xd.model.entity.TaskMemoryDO;

import java.util.List;

import java.util.List;

public interface TaskMemoryRepository {

    void save(TaskMemoryDO memory);

    List<TaskMemorySearchResultDTO> search(String workspaceId, float[] queryVector, int topK);

    TaskMemoryDO get(String taskId);

    void delete(String taskId);
}