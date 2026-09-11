package com.xd.service.impl;

import com.xd.assembler.AgentChatBlockAssembler;
import com.xd.assembler.AgentChatStreamAssembler;
import com.xd.assembler.AgentPhaseAggregator;
import com.xd.context.AgentChatAssembleContext;
import com.xd.context.AgentMessageProcessContext;
import com.xd.mapper.AgentTaskMapper;
import com.xd.mapper.ChatMessageMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.*;
import com.xd.model.vo.*;
import com.xd.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentChatAssemblerServiceImpl implements AgentChatAssemblerService {

    @Autowired
    private AgentEventService agentEventService;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private AgentFileChangeService agentFileChangeService;

    @Autowired
    private AgentChatBlockAssembler agentChatBlockAssembler;

    @Autowired
    private AgentChatStreamAssembler agentChatStreamAssembler;

    @Autowired
    private AgentPhaseAggregator agentPhaseAggregator;

    /**
     * 组装一个 Session 的完整 Agent Chat。
     *
     * Session
     * ├── Task 1 -> Turn 1
     * ├── Task 2 -> Turn 2
     * └── Task 3 -> Turn 3
     *
     * 当前 Task 和当前 Run 由参数明确指定。
     */
    @Override
    public AgentChatViewVO assemble(String sessionId) {
        /*
         * 1. 查询当前 Session
         *
         * Session 是 Chat View 的根上下文。
         */
        AgentSessionVO session = agentSessionService.getSessionById(sessionId);
        if (session == null) {
            return null;
        }

        /*
         * 2. 查询当前 Session 下所有 Task
         */
        List<AgentTaskDO> tasks = agentTaskMapper.selectBySessionId(sessionId);
        if (tasks == null) {
            tasks = List.of();
        }

        /*
         * 3. 按 Task 创建时间正序
         */
        List<AgentTaskDO> sortedTasks = tasks.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(AgentTaskDO::getCreatedAt, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(AgentTaskDO::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        /*
         * 4. 批量查询当前 Session 下所有 FileChange
         */
        List<String> taskIds = sortedTasks.stream()
                .map(AgentTaskDO::getTaskId)
                .filter(Objects::nonNull)
                .toList();

        List<AgentFileChangeDO> fileChanges = taskIds.isEmpty() ? List.of() : agentFileChangeService.getByTaskIds(taskIds);

        /*
         * 5. 构建：
         *
         * eventId -> FileChange
         *
         * 后续历史 Block 只需要通过 event.id
         * 在内存 Map 中找到 diffId。
         */
        Map<Long, AgentFileChangeDO> fileChangeMap = fileChanges.stream()
                .filter(Objects::nonNull)
                .filter(change -> change.getEventId() != null)
                .collect(Collectors.toMap(AgentFileChangeDO::getEventId, Function.identity(), (a, b) -> a));

        /*
         * 6. 构造历史组装 Context
         */
        AgentChatAssembleContext context = AgentChatAssembleContext.builder()
                .fileChanges(fileChangeMap)
                .build();

        /*
         * 7. 构造 Chat View
         */
        AgentChatViewVO chat = new AgentChatViewVO();
        chat.setSessionId(session.getSessionId());
        chat.setRootPath(session.getRootPath());
        /*
         * 8. Session → Workspace
         *
         * Workspace 可以为空。
         */
        String workspaceId = session.getWorkspaceId();
        chat.setWorkspaceId(workspaceId);

        if (workspaceId != null && !workspaceId.isBlank()) {
            WorkspaceDO workspace = workspaceService.getWorkspace(workspaceId);
            if (workspace != null) {
                chat.setWorkspaceName(workspace.getName());
            }
        }

        /*
         * 9. Session 最新 Task = 当前 Task
         */
        AgentTaskDO currentTask = sortedTasks.isEmpty() ? null : sortedTasks.get(sortedTasks.size() - 1);
        if (currentTask != null) {
            chat.setTaskId(currentTask.getTaskId());
            chat.setRunId(currentTask.getRunId());
        }

        /*
         * 10. 每个 Task = 一个 Chat Turn
         *
         * Phase 在 assembleTurn() 内部聚合。
         *
         * 一个 Turn 拥有自己的：
         * User Message
         * Agent Activity
         * Phases
         */
        List<AgentChatTurnVO> turns = new ArrayList<>();

        for (AgentTaskDO task : sortedTasks) {
            AgentChatTurnVO turn = assembleTurn(task, context);

            if (turn != null) {
                turns.add(turn);
            }
        }

        chat.setTurns(turns);

        return chat;
    }


    /**
     * 实时消息组装。
     *
     * 这个方法保持不变。
     */
    @Override
    public AgentChatStreamVO assemble(AgentMessageDTO agentMessageDTO, AgentMessageProcessContext messageProcessContext) {
        AgentChatStreamVO stream = agentChatStreamAssembler.assemble(agentMessageDTO, messageProcessContext);
        if (stream == null || agentMessageDTO.getTaskId() == null || agentMessageDTO.getRunId() == null
                || "RESULT_REFRESH".equals(stream.getType())) {
            return stream;
        }
        List<AgentEventDO> events = agentEventService.getEvents(agentMessageDTO.getTaskId(), agentMessageDTO.getRunId());
        if (events == null || events.isEmpty()) {
            return stream;
        }
        AgentChatAssembleContext context = buildRealtimeAssembleContext(agentMessageDTO.getTaskId());
        List<AgentChatBlockVO> blocks = agentChatBlockAssembler.assemble(events, context);
        List<AgentChatPhaseVO> phases = agentPhaseAggregator.aggregate(blocks);
        stream.setPhases(phases);
        return stream;
    }

    private AgentChatAssembleContext buildRealtimeAssembleContext(String taskId) {
        List<AgentFileChangeDO> fileChanges = agentFileChangeService.getByTaskIds(List.of(taskId));
        Map<Long, AgentFileChangeDO> fileChangeMap = fileChanges == null ? Collections.emptyMap() : fileChanges.stream()
                        .filter(Objects::nonNull)
                        .filter(change -> change.getEventId() != null)
                        .collect(Collectors.toMap(
                                AgentFileChangeDO::getEventId,
                                Function.identity(),
                                (a, b) -> a
                        ));
        return AgentChatAssembleContext.builder()
                .fileChanges(fileChangeMap)
                .build();
    }

    /**
     * 一个 Task = 一个 Chat Turn。
     *
     * Task 当前 runId 指向最新 Run。
     *
     * Retry：
     *
     * Task T001
     * ├── Run R001
     * └── Run R002 <- task.runId
     *
     * 最终 Turn 使用 R002 的 Event。
     */
    private AgentChatTurnVO assembleTurn(AgentTaskDO task, AgentChatAssembleContext context) {
        if (task == null || task.getTaskId() == null) {
            return null;
        }
        String taskId = task.getTaskId();
        String currentRunId = task.getRunId();

        /*
         * --------------------------------------------------
         * 1. 查询这个 Task 的全部 ChatMessage
         *
         * 不按 runId 查询。
         *
         * Retry 后：
         * USER Message 仍然属于这个 Task / Turn。
         * --------------------------------------------------
         */
        List<ChatMessageDO> messages = chatMessageMapper.selectByTaskIdAndRunId(taskId, null);
        if (messages == null) {
            messages = List.of();
        }

        /*
         * --------------------------------------------------
         * 2. 用户消息
         * --------------------------------------------------
         */
        ChatMessageDO userMessage = findUserMessage(messages);

        /*
         * --------------------------------------------------
         * 3. 当前 Task 当前 Run 的 Event
         *
         * Task.runId = 当前最新 Run。
         * --------------------------------------------------
         */
        List<AgentEventDO> events = new ArrayList<>();
        if (currentRunId != null && !currentRunId.isBlank()) {
            List<AgentEventDO> currentEvents = agentEventService.getEvents(taskId, currentRunId);
            if (currentEvents != null) {
                events.addAll(currentEvents);
            }
        }

        /*
         * --------------------------------------------------
         * 4. 构建 Agent
         *
         * Context 继续向下传递。
         * --------------------------------------------------
         */
        AgentChatViewVO.AgentMessageVO agent = buildAgentMessage(messages, events, context);

        /*
         * --------------------------------------------------
         * 5. 构建 Turn
         * --------------------------------------------------
         */
        AgentChatTurnVO turn = new AgentChatTurnVO();
        turn.setTaskId(taskId);
        turn.setRunId(currentRunId);
        turn.setUser(buildUserMessage(userMessage));
        turn.setAgent(agent);

        /*
         * --------------------------------------------------
         * 6. 当前 Turn 的 Agent Block -> Phase
         *
         * Phase 只属于当前 Turn，
         * 不再跨 Task 聚合。
         * --------------------------------------------------
         */
        if (agent != null && agent.getBlocks() != null && !agent.getBlocks().isEmpty()) {
            List<AgentChatPhaseVO> phases = agentPhaseAggregator.aggregate(agent.getBlocks());
            turn.setPhases(phases);
        }

        return turn;
    }

    /**
     * 组装一个 Turn 的 Agent 部分。
     */
    private AgentChatViewVO.AgentMessageVO buildAgentMessage(
            List<ChatMessageDO> messages,
            List<AgentEventDO> events,
            AgentChatAssembleContext context) {

        AgentChatViewVO.AgentMessageVO agent = new AgentChatViewVO.AgentMessageVO();

        /*
         * 主 Agent
         */
        agent.setAgentName(resolveMainAgent(events));

        /*
         * Event -> UI Block
         *
         * Context 中已经提前准备好了：
         *
         * eventId -> AgentFileChangeDO
         *
         * 下一层 AgentChatBlockAssembler
         * 会利用它为 FileChangeBlock 回填 diffId。
         */
        agent.setBlocks(agentChatBlockAssembler.assemble(events, context));

        /*
         * Assistant 最终回答
         *
         * 仍然来自 ChatMessage，
         * 不从 FINISH Event 获取。
         */
        agent.setFinalAnswer(buildFinalAnswer(messages));

        return agent;
    }

    /**
     * 组装用户消息。
     */
    private AgentChatViewVO.UserMessageVO buildUserMessage(ChatMessageDO message) {
        if (message == null) {
            return null;
        }

        AgentChatViewVO.UserMessageVO vo = new AgentChatViewVO.UserMessageVO();
        vo.setMessageId(message.getMessageId());
        vo.setContent(message.getContent());
        vo.setTimestamp(message.getCreatedAt());

        return vo;
    }

    /**
     * 组装最终 Assistant 回答。
     *
     * 一个 Task / Turn 的 Assistant
     * 取这个 Task 下最后一条 Assistant Message。
     *
     * Retry 后：
     * R001 ERROR
     * R002 FINISH
     *
     * 最终 Assistant Message 会属于 R002。
     */
    private AgentChatViewVO.FinalAnswerVO buildFinalAnswer(List<ChatMessageDO> messages) {
        ChatMessageDO message = findAssistantMessage(messages);
        if (message == null) {
            return null;
        }

        AgentChatViewVO.FinalAnswerVO vo = new AgentChatViewVO.FinalAnswerVO();
        vo.setType("final_answer");
        vo.setId(message.getMessageId());
        vo.setContent(message.getContent());
        vo.setTimestamp(message.getCreatedAt());

        return vo;
    }

    /**
     * Task 下的 User Message。
     *
     * 一个 Task 应该只有一条 USER Message。
     *
     * 使用最早一条作为保护。
     */
    private ChatMessageDO findUserMessage(List<ChatMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        return messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
                .min(Comparator.comparing(ChatMessageDO::getCreatedAt, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    /**
     * Task 下最后一条 Assistant Message。
     */
    private ChatMessageDO findAssistantMessage(List<ChatMessageDO> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        return messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> "ASSISTANT".equalsIgnoreCase(message.getRole()))
                .max(Comparator.comparing(ChatMessageDO::getCreatedAt, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
    }

    /**
     * 主 Agent：
     *
     * Supervisor 优先。
     * 没有则取第一个有效 Agent。
     */
    private String resolveMainAgent(List<AgentEventDO> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        String supervisor = events.stream()
                .filter(Objects::nonNull)
                .map(AgentEventDO::getAgentName)
                .filter(this::isNotBlank)
                .filter(name -> "Cando".equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        if (supervisor != null) {
            return supervisor;
        }

        return events.stream()
                .filter(Objects::nonNull)
                .map(AgentEventDO::getAgentName)
                .filter(this::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}