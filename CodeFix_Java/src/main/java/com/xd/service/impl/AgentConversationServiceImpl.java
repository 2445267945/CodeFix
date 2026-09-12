package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xd.mapper.AgentSessionMapper;
import com.xd.mapper.ChatMessageMapper;
import com.xd.model.context.ChatMessageContext;
import com.xd.model.context.SessionContext;
import com.xd.model.context.TaskRunContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.AgentTaskMessage;
import com.xd.model.dto.ChatMessageCreateDTO;
import com.xd.model.dto.TaskMemorySearchResultDTO;
import com.xd.model.entity.*;
import com.xd.model.enums.AgentRunCommandEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.vo.ChatMessageVO;
import com.xd.model.vo.SessionVO;
import com.xd.mq.MQProducer;
import com.xd.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationServiceImpl implements AgentConversationService {
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private AgentSessionMapper agentSessionMapper;
    @Autowired
    private ContextRetrievalService contextRetrievalService;
    @Autowired
    private TaskMemoryAsyncService taskMemoryAsyncService;
    @Autowired
    private TaskMemoryService taskMemoryService;
    @Autowired
    private MQProducer mqProducer;

    private static final int RECENT_TURN_COUNT = 3;
    private static final int MEMORY_TOP_K = 10;
    private static final double MEMORY_MIN_SCORE = 0.60;
    private static final double MEMORY_RELATIVE_RATIO = 0.85;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO sendMessage(ChatMessageCreateDTO request) {

        long now = System.currentTimeMillis();
        // 1. 先构造历史上下文
        SessionContext sessionContext = buildSessionContext(request.getSessionId(), request.getContent());

        // 2. 创建 Task + Run + Workspace 上下文
        TaskRunContext context = agentTaskService.createTaskWithRun(request);
        AgentSessionDO session = context.getSession();
        WorkspaceDO workspace = context.getWorkspace();
        AgentTaskDO task = context.getTask();
        AgentRunDO run = context.getRun();

        // 3. 保存当前 USER 消息
        String messageId = UUID.randomUUID().toString();
        ChatMessageDO message = new ChatMessageDO();
        message.setMessageId(messageId);
        message.setSessionId(session.getSessionId());
        message.setTaskId(task.getTaskId());
        message.setRunId(run.getRunId());
        message.setRole("USER");
        message.setContent(request.getContent());
        message.setCreatedAt(now);
        chatMessageMapper.insertChatMessage(message);

        // 4. 构造 Java → Python
        AgentTaskMessage agentMessage = new AgentTaskMessage();
        agentMessage.setVersion("1.0");
        agentMessage.setTimestamp(now);
        agentMessage.setMessageId(UUID.randomUUID().toString());
        agentMessage.setTaskId(task.getTaskId());
        agentMessage.setSessionId(session.getSessionId());
        agentMessage.setRunId(run.getRunId());
        agentMessage.setWorkspaceId(workspace.getWorkspaceId());
        agentMessage.setRootPath(workspace.getRootPath());
        agentMessage.setType("AGENT_TASK");
        agentMessage.setCommand(AgentRunCommandEnum.START.commandDesc_EN);
        agentMessage.setQuestion(request.getContent());
        agentMessage.setSessionContext(sessionContext);

        // 5. MQ
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentMessage));

        // 6. 返回 USER 消息
        return ChatMessageVO.builder()
                .messageId(messageId)
                .sessionId(session.getSessionId())
                .taskId(task.getTaskId())
                .runId(run.getRunId())
                .role("USER")
                .content(request.getContent())
                .createdAt(now)
                .build();
    }

    @Override
    public List<ChatMessageVO> getMessages(String sessionId) {
        // 确保 Session 存在
        agentSessionService.getSessionById(sessionId);

        List<ChatMessageDO> messages = chatMessageMapper.selectBySessionId(sessionId);
        return messages.stream()
                .map(message -> ChatMessageVO.builder()
                .messageId(message.getMessageId())
                .sessionId(message.getSessionId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build()
                ).toList();
    }

    @Override
    public List<SessionVO> getMessages() {
        List<AgentSessionDO> sessionsDO = agentSessionService.getSessions();
        ArrayList<SessionVO> sessionsVO = new ArrayList<>();
        for (AgentSessionDO session : sessionsDO) {
            SessionVO vo = SessionVO.builder()
                    .title(session.getTitle())
                    .sessionId(session.getSessionId())
                    .workspaceId(session.getWorkspaceId())
                    .createdAt(session.getCreatedAt())
                    .build();
            sessionsVO.add(vo);
        }
        return sessionsVO;
    }

    @Override
    public void saveAssistantMessage(AgentMessageDTO messageDTO) {
        if (messageDTO.getParentAgent() != null && !messageDTO.getParentAgent().isBlank()) {
            return;
        }
        if (!AgentEventEnum.FINISH.eventDesc.equals(messageDTO.getEvent())) {
            return;
        }
        ChatMessageDO message = new ChatMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId(messageDTO.getSessionId());
        message.setRole("ASSISTANT");
        message.setRunId(messageDTO.getRunId());
        message.setTaskId(messageDTO.getTaskId());
        message.setContent(extractAssistantContent(messageDTO));
        message.setCreatedAt(messageDTO.getTimestamp());
        chatMessageMapper.insertChatMessage(message);
        taskMemoryService.save(message.getTaskId());

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskMemoryAsyncService.save(message.getTaskId());
                    }
                }
        );
    }

    @Override
    public SessionContext buildSessionContext(String sessionId, String question) {
        AgentSessionDO session = agentSessionMapper.selectBySessionId(sessionId);
        if (session == null || !StringUtils.hasText(session.getWorkspaceId())) {
            return SessionContext.builder()
                    .messages(List.of())
                    .build();
        }
        // 最近几轮：短期上下文
        List<ChatMessageDO> recentMessages = chatMessageMapper.selectRecentBySessionId(List.of(sessionId), RECENT_TURN_COUNT);

        // 语义召回：长期上下文
        List<TaskMemorySearchResultDTO> memories = contextRetrievalService.retrieve(session.getWorkspaceId(), question, MEMORY_TOP_K);

        // 过滤低相关度 memory
        double maxSimilarity = memories.stream()
                .mapToDouble(TaskMemorySearchResultDTO::getSimilarity)
                .max()
                .orElse(0.0);
        double limit = Math.max(MEMORY_MIN_SCORE, maxSimilarity * MEMORY_RELATIVE_RATIO);
        List<TaskMemorySearchResultDTO> relevantMemories = memories.stream()
                .filter(memory -> memory.getSimilarity() >= limit)
                .toList();
        System.out.println("原召回：");
        System.out.println(memories);
        System.out.println("过滤后：");
        System.out.println(relevantMemories);

        List<String> taskIds = relevantMemories.stream()
                .map(TaskMemorySearchResultDTO::getMemory)
                .map(TaskMemoryDO::getMetaData)
                .map(TaskMemoryDO.MetaData::getTaskId)
                .distinct()
                .toList();

        List<ChatMessageDO> recalledMessages = Collections.emptyList();
        if (!taskIds.isEmpty()) {
            recalledMessages = chatMessageMapper.selectByTaskIds(taskIds);
        }
        List<ChatMessageContext> messages = buildContextMessages(recentMessages, recalledMessages);
        return SessionContext.builder()
                .messages(messages)
                .build();
    }

    private List<ChatMessageContext> buildContextMessages(List<ChatMessageDO> recentMessages, List<ChatMessageDO> recalledMessages) {
        Map<String, ChatMessageDO> uniqueMessages = new LinkedHashMap<>();
        if (recentMessages != null) {
            for (ChatMessageDO message : recentMessages) {
                if (message == null) {
                    continue;
                }
                uniqueMessages.putIfAbsent(String.valueOf(message.getId()), message);
            }
        }
        if (recalledMessages != null) {
            for (ChatMessageDO message : recalledMessages) {
                if (message == null) {
                    continue;
                }

                uniqueMessages.putIfAbsent(String.valueOf(message.getId()), message);
            }
        }
        List<ChatMessageDO> mergedMessages = new ArrayList<>(uniqueMessages.values());
        mergedMessages.sort(
                Comparator.comparing(ChatMessageDO::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        return mergedMessages.stream()
                .map(this::buildChatMessageContext)
                .toList();
    }

    private ChatMessageContext buildChatMessageContext(ChatMessageDO message) {
        return ChatMessageContext.builder()
                .role(message.getRole().toLowerCase())
                .content(message.getContent())
                .timestamp(message.getCreatedAt())
                .build();
    }

    private String extractAssistantContent(AgentMessageDTO messageDTO) {
        Map<String, Object> output = messageDTO.getOutput();
        if (output == null || output.isEmpty()) {
            return "任务已完成。";
        }
        String content = String.valueOf(output.get("content"));
        String reasoning = String.valueOf(output.get("reasoning"));
        return !content.isEmpty() ? content : reasoning;
    }
}
