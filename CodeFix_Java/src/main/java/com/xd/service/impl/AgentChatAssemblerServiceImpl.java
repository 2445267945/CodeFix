package com.xd.service.impl;

import com.xd.assembler.AgentChatBlockAssembler;
import com.xd.assembler.AgentChatStreamAssembler;
import com.xd.mapper.AgentTaskMapper;
import com.xd.mapper.ChatMessageMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.model.entity.ChatMessageDO;
import com.xd.model.vo.AgentChatBlockVO;
import com.xd.model.vo.AgentChatStreamVO;
import com.xd.model.vo.AgentChatTurnVO;
import com.xd.model.vo.AgentChatViewVO;
import com.xd.service.AgentChatAssemblerService;
import com.xd.service.AgentEventService;
import com.xd.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentChatAssemblerServiceImpl implements AgentChatAssemblerService {

    private final AgentEventService agentEventService;

    private final AgentRunService agentRunService;

    private final AgentTaskMapper agentTaskMapper;

    private final ChatMessageMapper chatMessageMapper;

    private final AgentChatBlockAssembler agentChatBlockAssembler;

    private final AgentChatStreamAssembler agentChatStreamAssembler;


    /**
     * 组装一个 Session 的完整 Agent Chat。
     * <p>
     * Session
     * ├── Task 1 -> Turn 1
     * ├── Task 2 -> Turn 2
     * └── Task 3 -> Turn 3
     * <p>
     * 当前 Task 和当前 Run 由参数明确指定。
     */
    @Override
    public AgentChatViewVO assemble(String sessionId) {

        /*
         * 1. 查询 Session 下所有 Task
         */
        List<AgentTaskDO> tasks = agentTaskMapper.selectBySessionId(sessionId);

        if (tasks == null) {
            tasks = List.of();
        }

        /*
         * 2. 按 Task 创建时间正序
         *
         * turns[]：
         * 最早 Task → 最新 Task
         */
        List<AgentTaskDO> sortedTasks = tasks.stream().filter(Objects::nonNull).sorted(Comparator.comparing(AgentTaskDO::getCreatedAt, Comparator.nullsLast(Long::compareTo))).toList();

        /*
         * 3. 组装 View
         */
        AgentChatViewVO chat = new AgentChatViewVO();

        chat.setSessionId(sessionId);

        /*
         * 4. Session 最新 Task = 当前 Task
         *
         * 因为每次新消息都会创建新的 Task，
         * 所以最后一个 Task 就是当前 Task。
         */
        AgentTaskDO currentTask = sortedTasks.isEmpty() ? null : sortedTasks.get(sortedTasks.size() - 1);

        if (currentTask != null) {
            chat.setTaskId(currentTask.getTaskId());

            chat.setRunId(currentTask.getRunId());
        }

        /*
         * 5. 每一个 Task = 一个 Chat Turn
         */
        List<AgentChatTurnVO> turns = new ArrayList<>();

        for (AgentTaskDO task : sortedTasks) {

            AgentChatTurnVO turn = assembleTurn(task);

            if (turn != null) {
                turns.add(turn);
            }
        }

        chat.setTurns(turns);

        return chat;
    }


    /**
     * 实时消息组装。
     * <p>
     * 这个方法保持不变。
     */
    @Override
    public AgentChatStreamVO assemble(AgentMessageDTO agentMessageDTO) {

        return agentChatStreamAssembler.assemble(agentMessageDTO);
    }


    /**
     * 一个 Task = 一个 Chat Turn。
     * <p>
     * Task 当前 runId 指向最新 Run。
     * <p>
     * Retry：
     * <p>
     * Task T001
     * ├── Run R001
     * └── Run R002  <- task.runId
     * <p>
     * 最终 Turn 使用 R002 的 Event。
     */
    private AgentChatTurnVO assembleTurn(AgentTaskDO task) {

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
         * 原因：
         * Retry 后 USER Message 可能属于旧 Run，
         * 但它仍然属于这个 Task / Turn。
         * --------------------------------------------------
         */
        List<ChatMessageDO> messages = chatMessageMapper.selectByTaskIdAndRunId(taskId, null);

        if (messages == null) {
            messages = List.of();
        }

        /*
         * --------------------------------------------------
         * 2. 用户消息
         * 一个 Task 对应一条 USER Message。
         * --------------------------------------------------
         */
        ChatMessageDO userMessage = findUserMessage(messages);

        /*
         * --------------------------------------------------
         * 3. 当前 Task 当前 Run 的 Event
         *
         * Task.runId 就是当前最新 Run。
         * Retry 后这里自然得到新的 Run。
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
         * 4. 构建 Turn
         * --------------------------------------------------
         */
        AgentChatTurnVO turn = new AgentChatTurnVO();

        /*
         * Task 就是这一轮，所以：
         * turnId 不再存在。
         *
         * 如果你的 AgentChatTurnVO 还有 turnId，
         * 建议已经删除。
         */
        turn.setTaskId(taskId);

        turn.setRunId(currentRunId);

        turn.setUser(buildUserMessage(userMessage));

        turn.setAgent(buildAgentMessage(messages, events));

        return turn;
    }


    /**
     * 组装一个 Turn 的 Agent 部分。
     */
    private AgentChatViewVO.AgentMessageVO buildAgentMessage(List<ChatMessageDO> messages, List<AgentEventDO> events) {

        AgentChatViewVO.AgentMessageVO agent = new AgentChatViewVO.AgentMessageVO();

        /*
         * 主 Agent
         */
        agent.setAgentName(resolveMainAgent(events));

        /*
         * Event -> UI Block
         */
        agent.setBlocks(agentChatBlockAssembler.assemble(events));

        /*
         * Assistant 最终回答
         *
         * 从 ChatMessage 取，
         * 不从 FINISH Event 取。
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
     * <p>
     * 一个 Task / Turn 的 Assistant
     * 取这个 Task 下最后一条 Assistant Message。
     * <p>
     * Retry 后：
     * R001 ERROR
     * R002 FINISH
     * <p>
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
     * <p>
     * 一个 Task 应该只有一条 USER Message。
     * <p>
     * 使用最早一条作为保护。
     */
    private ChatMessageDO findUserMessage(List<ChatMessageDO> messages) {

        if (messages == null || messages.isEmpty()) {
            return null;
        }

        return messages.stream().filter(Objects::nonNull).filter(message -> "USER".equalsIgnoreCase(message.getRole())).min(Comparator.comparing(ChatMessageDO::getCreatedAt, Comparator.nullsLast(Long::compareTo))).orElse(null);
    }


    /**
     * Task 下最后一条 Assistant Message。
     */
    private ChatMessageDO findAssistantMessage(List<ChatMessageDO> messages) {

        if (messages == null || messages.isEmpty()) {
            return null;
        }

        return messages.stream().filter(Objects::nonNull).filter(message -> "ASSISTANT".equalsIgnoreCase(message.getRole())).max(Comparator.comparing(ChatMessageDO::getCreatedAt, Comparator.nullsLast(Long::compareTo))).orElse(null);
    }


    /**
     * 主 Agent：
     * <p>
     * Supervisor 优先。
     * 没有则取第一个有效 Agent。
     */
    private String resolveMainAgent(List<AgentEventDO> events) {

        if (events == null || events.isEmpty()) {
            return null;
        }

        String supervisor = events.stream().filter(Objects::nonNull).map(AgentEventDO::getAgentName).filter(this::isNotBlank).filter(name -> "Supervisor".equalsIgnoreCase(name)).findFirst().orElse(null);

        if (supervisor != null) {
            return supervisor;
        }

        return events.stream().filter(Objects::nonNull).map(AgentEventDO::getAgentName).filter(this::isNotBlank).findFirst().orElse(null);
    }


    private boolean isNotBlank(String value) {

        return value != null && !value.isBlank();
    }
}