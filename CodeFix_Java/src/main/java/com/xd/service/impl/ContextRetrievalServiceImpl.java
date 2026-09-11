package com.xd.service.impl;

import com.xd.model.dto.TaskMemorySearchResultDTO;
import com.xd.repository.TaskMemoryRepository;
import com.xd.service.ContextRetrievalService;
import com.xd.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContextRetrievalServiceImpl implements ContextRetrievalService {

    private final EmbeddingService embeddingService;
    private final TaskMemoryRepository taskMemoryRepository;

    @Override
    public List<TaskMemorySearchResultDTO> retrieve(String workspaceId, String question, int topK) {
        if (!StringUtils.hasText(workspaceId) || !StringUtils.hasText(question) || topK <= 0) {
            return List.of();
        }
        float[] queryVector = embeddingService.embed(question);
        return taskMemoryRepository.search(workspaceId, queryVector, topK);
    }
}
