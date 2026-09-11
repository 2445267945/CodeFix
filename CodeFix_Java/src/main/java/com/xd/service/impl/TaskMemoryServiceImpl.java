package com.xd.service.impl;

import com.xd.mapper.AgentSessionMapper;
import com.xd.mapper.AgentTaskMapper;
import com.xd.mapper.ChatMessageMapper;
import com.xd.mapper.WorkSpaceMapper;
import com.xd.model.entity.*;
import com.xd.repository.TaskMemoryRepository;
import com.xd.service.EmbeddingService;
import com.xd.service.TaskMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskMemoryServiceImpl implements TaskMemoryService {

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Autowired
    private AgentSessionMapper agentSessionMapper;

    @Autowired
    private WorkSpaceMapper workspaceMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private TaskMemoryRepository taskMemoryRepository;

    @Override
    public TaskMemoryDO buildFromTask(String taskId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            return null;
        }
        AgentSessionDO session = agentSessionMapper.selectBySessionId(task.getSessionId());
        WorkspaceDO workspace = null;
        if (session != null && session.getWorkspaceId() != null) {
            workspace = workspaceMapper.selectByWorkspaceId(session.getWorkspaceId());
        }
        List<ChatMessageDO> messages = chatMessageMapper.selectByTaskIds(List.of(taskId));
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String userQuestion = null;
        String finalAnswer = null;
        for (ChatMessageDO message : messages) {
            if ("USER".equals(message.getRole()) && userQuestion == null) {
                userQuestion = message.getContent();
            }
            if ("ASSISTANT".equals(message.getRole())) {
                finalAnswer = message.getContent();
            }
        }

        if (userQuestion == null || finalAnswer == null) {
            return null;
        }

        String workspaceId = session == null ? null : session.getWorkspaceId();
        String workspaceName = workspace == null ? null : workspace.getName();

        /*
         * retrievalText 仅用于当前 Task 的 embedding，
         * 不作为 Redis 持久化字段。
         */
        String retrievalText = buildRetrievalText(userQuestion, finalAnswer, workspaceName);

        float[] embedding = embeddingService.embed(retrievalText);
        TaskMemoryDO.MetaData metaData = TaskMemoryDO.MetaData.builder()
                .taskId(taskId)
                .sessionId(task.getSessionId())
                .workspaceId(workspaceId)
                .workspaceName(workspaceName)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();

        return TaskMemoryDO.builder()
                .embedding(embedding)
                .metaData(metaData)
                .build();
    }

    @Override
    public void save(String taskId) {
        TaskMemoryDO memory = buildFromTask(taskId);
        if (memory == null) return;
        taskMemoryRepository.save(memory);
    }

    @Override
    public void rebuild(String taskId) {
        save(taskId);
    }

    @Override
    public TaskMemoryDO get(String taskId) {
        return taskMemoryRepository.get(taskId);
    }

    @Override
    public void delete(String taskId) {
        taskMemoryRepository.delete(taskId);
    }

    private String buildRetrievalText(String userQuestion, String finalAnswer, String workspaceName) {
        return "[QUESTION]\n" + userQuestion + "\n\n[FINAL_ANSWER]\n" + finalAnswer + "\n\n[WORKSPACE]\n" + (workspaceName == null ? "" : workspaceName);
    }
}