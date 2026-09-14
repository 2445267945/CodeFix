package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.exception.BusinessException;
import com.xd.mapper.*;
import com.xd.model.context.TaskRunContext;
import com.xd.model.dto.*;
import com.xd.model.entity.*;
import com.xd.model.enums.*;
import com.xd.model.vo.*;
import com.xd.mq.MQProducer;
import com.xd.runtime.permission.PermissionRuntimeStore;
import com.xd.service.*;
import com.xd.runtime.state.AgentStateTransitionResult;
import com.xd.runtime.state.AgentTaskStateMachine;
import com.xd.runtime.state.AgentTransitionContext;
import com.xd.runtime.state.AgentTransitionGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.xd.model.enums.AgentEventEnum.TOOL_RESULT;
import static com.xd.model.enums.AgentTaskStatusEnum.WAITING_HUMAN;

@Slf4j
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    @Autowired
    private AgentTaskMapper agentTaskMapper;
    @Autowired
    private AgentRunService agentRunService;
    @Autowired
    private AgentEventMapper agentEventMapper;
    @Autowired
    private AgentRunMapper agentRunMapper;
    @Autowired
    private AgentSessionMapper agentSessionMapper;
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AgentChatAssemblerService agentChatAssemblerService;
    @Autowired
    private AgentTaskStateMachine stateMachine;
    @Autowired
    private AgentRunStateHistoryMapper stateHistoryMapper;
    @Autowired
    private AgentTransitionGuard agentTransitionGuard;
    @Autowired
    private PermissionRuntimeStore permissionRuntimeStore;
    @Autowired
    private AgentEventService agentEventService;
    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskRunContext createTaskWithRun(ChatMessageCreateDTO request) {
        String runId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String sessionId = request.getSessionId();
        String question = request.getContent();

        // 1. 获取 / 创建 Session
        AgentSessionDO session = agentSessionService.getOrCreateSession(sessionId, question);

        String actualSessionId = session.getSessionId();
        String workspaceId = session.getWorkspaceId();

        WorkspaceDO workspace;
        if (StringUtils.hasText(workspaceId)) {
            // Session 已经绑定 Workspace
            workspace = workspaceService.getWorkspace(workspaceId);
        } else {
            // 第一次真正发起对话
            String workspacePath = request.getWorkspacePath();
            if (!StringUtils.hasText(workspacePath)) {
                throw new IllegalStateException("第一次对话需要选择工作空间");
            }
            // 先按照路径查 Workspace
            workspace = workspaceService.getWorkspaceByRootPath(workspacePath);
            // 不存在则创建
            if (workspace == null) {
                WorkspaceFileUpdateDTO workspaceRequest = new WorkspaceFileUpdateDTO();
                workspaceRequest.setPath(workspacePath);
                workspace = workspaceService.createWorkspace(workspaceRequest);
            }
            // Session 绑定 Workspace
            session.setWorkspaceId(workspace.getWorkspaceId());
            session.setUpdatedAt(now);
            agentSessionService.bindWorkspace(actualSessionId, workspace.getWorkspaceId());
        }

        // 4. 创建 Task
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskId(taskId);
        task.setRunId(runId);
        task.setSessionId(actualSessionId);
        task.setQuestion(question);
        task.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion("1.0");

        // 5. 创建 Run
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(actualSessionId);
        run.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        run.setAttempt(1);
        run.setPermissionProfile(request.getPermissionProfile());
        run.setStartedAt(now);
        run.setCreatedAt(now);

        // 6. 持久化 Task / Run
        agentTaskMapper.insertAgentTask(task);
        agentRunMapper.insertAgentRun(run);

        // 7. 返回完整执行上下文
        return TaskRunContext.builder()
                .session(session)
                .workspace(workspace)
                .task(task)
                .run(run)
                .build();
    }

    @Override
    public List<TaskDetailVO> getTasksBySessionId(String sessionId) {
        List<AgentTaskDO> tasks = agentTaskMapper.selectBySessionId(sessionId);
        return tasks.stream()
                .map(this::toTaskDetailVO).toList();
    }

    @Override
    public void updateTaskStatus(AgentMessageDTO agentMessageDTO) {
        if (agentMessageDTO.getParentAgent() != null && !agentMessageDTO.getParentAgent().isBlank()) {
            return;
        }
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByDesc(agentMessageDTO.getStatus());
        if (status == null) {
            log.info("当前状态不在可维护状态中");
            return;
        }
        AgentTaskDO agentTaskDO = new AgentTaskDO();
        agentTaskDO.setTaskId(agentMessageDTO.getTaskId());
        agentTaskDO.setSessionId(agentMessageDTO.getSessionId());
        agentTaskDO.setStatus(AgentTaskStatusEnum.getStatusByDesc(agentMessageDTO.getStatus()).statusCode);
        agentTaskDO.setUpdatedAt(agentMessageDTO.getTimestamp());
        agentTaskMapper.updateTask(agentTaskDO);
    }


    @Override
    public TaskDetailVO getTask(String taskId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        return toTaskDetailVO(task);
    }

    @Override
    public List<AgentEventVO> getTaskEvents(String taskId, String runId) {
        List<AgentEventDO> events;
        if (runId == null || runId.isBlank()) {
            events = agentEventMapper.selectByTaskId(taskId);
        } else {
            events = agentEventMapper.selectByTaskIdAndRunId(taskId, runId);
        }
        return events.stream()
                .map(this::toEventVO)
                .toList();
    }

    private AgentEventVO toEventVO(AgentEventDO event) {
        AgentEventVO vo = new AgentEventVO();
        vo.setMessageId(event.getMessageId());
        vo.setRunId(event.getRunId());
        vo.setTaskId(event.getTaskId());
        vo.setSessionId(event.getSessionId());
        vo.setAgentName(event.getAgentName());
        vo.setParentAgent(event.getParentAgent());
        vo.setEvent(event.getEvent());
        vo.setStep(event.getStep());
        vo.setStatus(event.getStatus());
        vo.setTimestamp(event.getEventTimestamp());
        if (event.getOutput() != null && !event.getOutput().isBlank()) {
            try {
                Map<String, Object> output = objectMapper.readValue(event.getOutput(), new TypeReference<Map<String, Object>>() {
                });
                vo.setOutput(output);
            } catch (JsonProcessingException e) {
                log.warn("Agent Event output 解析失败: messageId={}", event.getMessageId(), e);
                vo.setOutput(Collections.emptyMap());
            }
        }

        return vo;
    }

    @Override
    public TaskOperateVO resumeTask(String taskId, String runId) {

        final String[] sessionIdHolder = new String[1];

        AgentStateTransitionResult result = transactionTemplate.execute(status -> {

            // 1. 查询 Task
            AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
            if (task == null) {
                throw new IllegalStateException("Task 不存在: taskId=" + taskId);
            }
            // 2. 查询 Run
            AgentRunDO run = agentRunMapper.selectByRunId(runId);
            if (run == null) {
                throw new IllegalStateException("Run 不存在: runId=" + runId);
            }
            // 3. 确认当前 Task 绑定的就是这个 Run
            if (!runId.equals(task.getRunId())) {
                throw new IllegalStateException("runId 不是当前 Task 的最新 Run: " + "taskId=" + taskId + ", 当前=" + task.getRunId() + ", 接收=" + runId);
            }

            sessionIdHolder[0] = task.getSessionId();

            // 4. 当前状态
            AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());
            // 5. State Machine：CANCELLED -> AGENT_THINKING
            AgentTaskStatusEnum nextState = stateMachine.transition(currentState, AgentRunCommandEnum.RESUME);

            // 6. 更新 Task 状态
            AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
            updateMessageDTO.setTaskId(taskId);
            updateMessageDTO.setRunId(runId);
            updateMessageDTO.setStatus(nextState.statusDesc_EN);
            updateMessageDTO.setSessionId(task.getSessionId());
            updateTaskStatus(updateMessageDTO);

            // 7. 写 State History
            AgentRunStateHistoryDO history = new AgentRunStateHistoryDO();
            history.setTaskId(taskId);
            history.setRunId(runId);
            history.setFromStatus(currentState.statusDesc_EN);
            history.setTriggerType("COMMAND");
            history.setTrigger(AgentRunCommandEnum.RESUME.commandDesc_EN);
            history.setToStatus(nextState.statusDesc_EN);
            history.setMessageId(null);
            history.setStep(null);
            history.setReason(buildRunCommandReason(currentState, AgentRunCommandEnum.RESUME, nextState, run.getActionId()));
            history.setCreatedAt(System.currentTimeMillis());
            stateHistoryMapper.insertStateHistory(history);

            log.info(
                    "Agent Runtime Resume 状态迁移: taskId={}, runId={}, {} -> {}, command=RESUME",
                    taskId,
                    runId,
                    currentState.statusDesc_EN,
                    nextState.statusDesc_EN
            );

            return AgentStateTransitionResult.builder()
                    .sessionId(task.getSessionId())
                    .changed(currentState != nextState)
                    .fromStatus(currentState)
                    .toStatus(nextState)
                    .actionId(run.getActionId())
                    .triggerType("COMMAND")
                    .trigger(AgentRunCommandEnum.RESUME.commandDesc_EN)
                    .reason(buildRunCommandReason(currentState, AgentRunCommandEnum.RESUME, nextState, run.getActionId()))
                    .build();
        });

        if (result == null) {
            throw new IllegalStateException("Agent Resume 状态迁移失败: taskId=" + taskId);
        }

        // 8. Java 事务提交成功后，再通知 Python Runtime
        // 得到workspaceId
        AgentSessionDO agentSessionDO = agentSessionMapper.selectBySessionId(result.getSessionId());
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setSessionId(sessionIdHolder[0]);
        agentTaskMessage.setRunId(runId);
        agentTaskMessage.setWorkspaceId(agentSessionDO.getWorkspaceId());
        agentTaskMessage.setType("AGENT_TASK");
        agentTaskMessage.setCommand(AgentRunCommandEnum.RESUME.commandDesc_EN);
        agentTaskMessage.setVersion("1.0");
        agentTaskMessage.setTaskId(taskId);
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentTaskMessage));

        return TaskOperateVO.builder()
                .message("任务已继续执行")
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskOperateVO retryTask(String taskId) {
        // 1. 查询原任务
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        AgentSessionDO agentSessionDO = agentSessionMapper.selectBySessionId(task.getSessionId());
        // 2. 生成新的 Run
        String newRunId = UUID.randomUUID().toString();
        // 3. 查询当前任务已经执行到第几次
        Integer maxAttempt = agentRunMapper.selectMaxAttemptByTaskId(taskId);
        int newAttempt = (maxAttempt == null ? 0 : maxAttempt) + 1;
        long now = System.currentTimeMillis();
        // 4. 插入新的 AgentRun
        AgentRunDO run = new AgentRunDO();
        run.setRunId(newRunId);
        run.setTaskId(taskId);
        run.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        run.setSessionId(task.getSessionId());
        run.setAttempt(newAttempt);
        run.setCreatedAt(now);
        run.setStartedAt(now);
        agentRunMapper.insertAgentRun(run);

        // 5. 更新 Task 当前 Run
        AgentTaskDO update = new AgentTaskDO();
        update.setTaskId(taskId);
        update.setRunId(newRunId);
        update.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        update.setUpdatedAt(System.currentTimeMillis());
        agentTaskMapper.updateTask(update);

        // 6. 构造新的 AgentTaskMessage
        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setTaskId(taskId);
        msg.setSessionId(task.getSessionId());
        msg.setRunId(newRunId);
        msg.setWorkspaceId(agentSessionDO.getWorkspaceId());
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setVersion("1.0");
        msg.setTimestamp(now);
        msg.setType("AGENT_TASK");
        // Retry = 创建新Run，然后按照START执行
        msg.setCommand(AgentRunCommandEnum.START.commandDesc_EN);
        msg.setQuestion(task.getQuestion());
        // 7. 投递 MQ
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));
        // 8. 返回
        return TaskOperateVO.builder()
                .taskId(taskId)
                .runId(newRunId)
                .status(AgentTaskStatusEnum.AGENT_THINKING.statusCode)
                .statusValue("CREATED")
                .message("任务已重新执行")
                .build();
    }


    @Override
    public TaskResultVO getTaskResult(String taskId, String runId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        String targetRunId = (runId == null || runId.isBlank()) ? task.getRunId() : runId;
        AgentRunDO run = agentRunService.getRun(taskId, targetRunId);
        AgentChatViewVO chat = agentChatAssemblerService.assemble(task.getSessionId());
        return buildTaskResultVO(task, run, chat);
    }

    @Override
    public List<TaskDetailVO> getTasks() {
        List<AgentTaskDO> tasks = agentTaskMapper.selectTaskList();
        return tasks.stream()
                .map(this::toTaskDetailVO)
                .toList();
    }


    private AgentRunVO toAgentRunVO(AgentRunDO run, String currentRunId) {
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByCode(run.getStatus());
        return AgentRunVO.builder()
                .runId(run.getRunId())
                .taskId(run.getTaskId())
                .sessionId(run.getSessionId())
                .attempt(run.getAttempt())
                .status(run.getStatus())
                .statusValue(status == null ? "UNKNOWN" : status.statusDesc_EN)
                .startedAt(run.getStartedAt())
                .endedAt(run.getEndedAt())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .current(run.getRunId()
                .equals(currentRunId))
                .build();
    }

    private TaskDetailVO toTaskDetailVO(AgentTaskDO task) {
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByCode(task.getStatus());
        if (status == null) {
            throw new BusinessException("未知任务状态: " + task.getStatus());
        }
        return TaskDetailVO.builder()
                .taskId(task.getTaskId())
                .sessionId(task.getSessionId())
                .runId(task.getRunId())
                .status(task.getStatus())
                .statusValue(status.statusDesc_EN)
                .question(task.getQuestion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private TaskResultVO buildTaskResultVO(AgentTaskDO task, AgentRunDO run, AgentChatViewVO chat) {
        return TaskResultVO.builder()
                .taskId(task.getTaskId())
                .runId(run.getRunId())
                .status(AgentTaskStatusEnum.getStatusByCode(run.getStatus()).statusDesc_EN)
                .chat(chat).build();
    }


    /**
     * 处理 Agent Runtime 内部事件。
     *
     * 注意：
     *
     * 这里只处理 Root Agent。
     *
     * 子 Agent 事件虽然会落 AgentEvent，
     * 但不直接驱动当前 Task 的 Runtime State。
     */
    @Override
    public AgentStateTransitionResult handleAgentEvent(AgentMessageDTO messageDTO) {
        if (messageDTO == null) {
            return null;
        }
        /*
         * 只有 Root Agent 才能驱动当前 Task 状态。
         */
        if (messageDTO.getParentAgent() != null && !messageDTO.getParentAgent().isBlank()) {
            return null;
        }
        /*
         * Agent Event。
         */
        AgentEventEnum event = parseEvent(messageDTO.getEvent());
        if (event == null) {
            log.debug(
                    "忽略未知 Agent Event: taskId={}, runId={}, event={}",
                    messageDTO.getTaskId(),
                    messageDTO.getRunId(),
                    messageDTO.getEvent()
            );
            return null;
        }

        /*
         * 读取当前 Task 状态。
         *
         * 这里的 Task.status 是唯一 Runtime State。
         */
        AgentTaskDO task = agentTaskMapper.selectByTaskId(messageDTO.getTaskId());
        if (task == null) {
            log.info("Task 不存在: {}", messageDTO.getTaskId());
            return null;
        }
        AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

        if (currentState == WAITING_HUMAN && event == TOOL_RESULT) {
            log.info("等待审批期间收到 TOOL_RESULT，视为取消收尾结果，不驱动状态迁移: taskId={}, runId={}", messageDTO.getTaskId(), messageDTO.getRunId());

            return AgentStateTransitionResult.builder()
                    .changed(false)
                    .fromStatus(currentState)
                    .toStatus(currentState)
                    .triggerType("EVENT")
                    .trigger(event.eventDesc)
                    .reason("等待审批期间的 Tool Result，仅用于闭合 Tool Call")
                    .build();
        }
        /*
         * 状态机计算下一状态。
         */
        AgentTaskStatusEnum nextState = null;
        nextState = stateMachine.transition(currentState, event);
        /*
         * 当前 Event 没有导致状态变化。
         *
         * 例如：
         *
         * THINKING + THINK
         * -> THINKING
         *
         * Event 本身已经由 AgentEventService 落库，
         * 所以这里不需要写 StateHistory。
         */
        if (nextState == currentState) {
            return AgentStateTransitionResult.builder()
                    .changed(false)
                    .fromStatus(currentState)
                    .toStatus(currentState)
                    .triggerType("EVENT")
                    .trigger(event.eventDesc)
                    .reason("Agent Event 未导致 Runtime 状态变化")
                    .build();
        }


        /*
         * 状态发生变化。
         */
        AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
        updateMessageDTO.setTaskId(messageDTO.getTaskId());
        updateMessageDTO.setStatus(nextState.statusDesc_EN);
        updateMessageDTO.setSessionId(messageDTO.getSessionId());
        updateTaskStatus(updateMessageDTO);
        /*
         * 写 State History。
         */
        AgentRunStateHistoryDO history = new AgentRunStateHistoryDO();
        history.setTaskId(messageDTO.getTaskId());
        history.setRunId(messageDTO.getRunId());
        history.setFromStatus(currentState.statusDesc_EN);
        history.setTriggerType("EVENT");
        history.setTrigger(event.eventDesc);
        history.setToStatus(nextState.statusDesc_EN);
        history.setMessageId(messageDTO.getMessageId());
        history.setStep(Integer.valueOf(messageDTO.getStep()));
        history.setReason(buildReason(currentState, event, nextState));
        history.setCreatedAt(messageDTO.getTimestamp());
        stateHistoryMapper.insertStateHistory(history);
        log.info(
                "Agent Runtime 状态迁移: taskId={}, runId={}, {} -> {}, event={}",
                messageDTO.getTaskId(),
                messageDTO.getRunId(),
                currentState.statusDesc_EN,
                nextState.statusDesc_EN,
                event.eventDesc
        );
        return AgentStateTransitionResult.builder()
                .changed(true)
                .fromStatus(currentState)
                .toStatus(nextState)
                .triggerType("EVENT")
                .trigger(event.eventDesc)
                .reason(buildReason(currentState, event, nextState))
                .build();
    }

    @Override
    public AgentStateTransitionResult handleCommand(String taskId, String runId, String actionId, String command) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }

        // 1. 解析 Action Command
        AgentActionCommandEnum actionCommand;
        try {
            actionCommand = AgentActionCommandEnum.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知 Agent Action Command: " + command, e);
        }

        // 2. 状态更新 + History 必须在同一事务
        AgentStateTransitionResult result = transactionTemplate.execute(status -> {
            // 3. 查询当前 Task
            AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
            AgentRunDO run = agentRunMapper.selectByTaskId(taskId).get(0);
            if (task == null || run == null) {
                throw new IllegalStateException("Task或Run 不存在: taskId:" + taskId + ", runId:" + runId);
            }
            if (!task.getRunId().equals(runId)) {
                throw new IllegalStateException("runId不是当前的最新: 当前:" + task.getRunId() + ", 接收:" + runId);
            }

            // 4. 当前状态
            AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

            // 5. Guard Context
            AgentTransitionContext context = AgentTransitionContext.builder()
                    .taskId(taskId)
                    .runId(runId)
                    .actionId(actionId)
                    .state(currentState)
                    .build();

            // 6. Guard
            boolean allowed = switch (actionCommand) {
                case APPROVE -> agentTransitionGuard.canApprove(context, run.getActionId());
                case REJECT -> agentTransitionGuard.canReject(context, run.getActionId());
            };

            if (!allowed) {
                throw new IllegalStateException(
                        "当前状态不允许执行 Action Command: "
                                + "taskId=" + taskId
                                + ", runId=" + runId
                                + ", actionId=" + actionId
                                + ", command=" + actionCommand
                                + ", currentState=" + currentState
                );
            }

            // 7. State Machine 计算下一状态
            AgentTaskStatusEnum nextState = stateMachine.transition(currentState, actionCommand);

            // 8. 更新 Task 当前状态
            AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
            updateMessageDTO.setTaskId(taskId);
            updateMessageDTO.setRunId(runId);
            updateMessageDTO.setStatus(nextState.statusDesc_EN);
            updateTaskStatus(updateMessageDTO);

            // 9. 写 State History
            AgentRunStateHistoryDO history = new AgentRunStateHistoryDO();
            history.setTaskId(taskId);
            history.setRunId(runId);
            history.setFromStatus(currentState.statusDesc_EN);
            history.setTriggerType("COMMAND");
            history.setTrigger(actionCommand.commandDesc_EN);
            history.setToStatus(nextState.statusDesc_EN);
            // Command 不是 Python Agent Event，
            // 因此这里没有 messageId / step 的来源。
            history.setMessageId(null);
            history.setStep(null);
            history.setReason(buildCommandReason(currentState, actionCommand, nextState, actionId));
            history.setCreatedAt(System.currentTimeMillis());
            stateHistoryMapper.insertStateHistory(history);

            log.info(
                    "Agent Runtime Command 状态迁移: taskId={}, runId={}, actionId={}, {} -> {}, command={}",
                    taskId,
                    runId,
                    actionId,
                    currentState.statusDesc_EN,
                    nextState.statusDesc_EN,
                    actionCommand.commandDesc_EN
            );

            // 10. 返回迁移结果
            return AgentStateTransitionResult.builder()
                    .changed(currentState != nextState)
                    .fromStatus(currentState)
                    .toStatus(nextState)
                    .actionId(run.getActionId())
                    .triggerType("COMMAND")
                    .trigger(actionCommand.commandDesc_EN)
                    .reason(buildCommandReason(currentState, actionCommand, nextState, actionId))
                    .build();
        });
        if (result == null) {
            throw new IllegalStateException("Agent Command 状态迁移失败: taskId=" + taskId);
        }

        AgentTaskMessage agentMessage = new AgentTaskMessage();
        agentMessage.setVersion("1.0");
        agentMessage.setTimestamp(System.currentTimeMillis());
        agentMessage.setMessageId(UUID.randomUUID().toString());
        agentMessage.setTaskId(taskId);
        agentMessage.setRunId(runId);
        agentMessage.setType("AGENT_COMMAND");
        agentMessage.setCommand(actionCommand.commandDesc_EN);
        agentMessage.setCommandType("ACTION");
        agentMessage.setActionId(actionId);
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentMessage));

        return result;
    }

    @Override
    public TaskOperateVO cancelTask(String taskId, String runId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        AgentRunDO run = agentRunMapper.selectByRunId(runId);
        if (task == null) {
            throw new IllegalStateException("Task 不存在: taskId=" + taskId);
        }
        if (run == null) {
            throw new IllegalStateException("Run 不存在: runId=" + runId);
        }
        if (!runId.equals(task.getRunId())) {
            throw new IllegalStateException("runId 不是当前 Task 的最新 Run: " + "taskId=" + taskId + ", 当前=" + task.getRunId() + ", 接收=" + runId);
        }
        // 发送 CANCEL 给 Python Runtime
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setType("AGENT_TASK");
        agentTaskMessage.setCommand(AgentRunCommandEnum.CANCEL.commandDesc_EN);
        agentTaskMessage.setVersion("1.0");
        agentTaskMessage.setTaskId(taskId);
        agentTaskMessage.setRunId(runId);
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentTaskMessage));

        return TaskOperateVO.builder()
                .message("任务已发送取消请求")
                .build();
    }

    @Override
    public AgentStateTransitionResult handleUserCommand(String runId, String command) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }
        AgentActionCommandEnum actionCommand;
        try {
            actionCommand = AgentActionCommandEnum.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知 Agent Action Command: " + command, e);
        }

        AgentRunDO agentRunDO = agentRunMapper.selectByRunId(runId);
        String taskId = agentRunDO.getTaskId();
        String actionId = agentRunDO.getActionId();
        /*
         * 人工拒绝：
         * 只执行 REJECT，不写 Permission Cache。
         */
        if (actionCommand == AgentActionCommandEnum.REJECT) {
            return handleCommand(taskId, runId, actionId, actionCommand.commandDesc_EN);
        }

        /*
         * 人工允许：
         * 先找到当前 TOOL_WAITING，
         * 从中恢复 Tool 信息。
         */
        AgentEventDO waitingEvent = agentEventService.getToolWaitingByActionId(taskId, runId, actionId);

        if (waitingEvent == null) {
            throw new IllegalStateException("找不到对应的 TOOL_WAITING Event: " + "taskId=" + taskId + ", runId=" + runId + ", actionId=" + actionId);
        }
        Map<String, Object> data;
        try {
            data = JSON.parseObject(waitingEvent.getOutput());
        } catch (Exception e) {
            throw new IllegalStateException("TOOL_WAITING output 解析失败: actionId=" + actionId, e);
        }
        String toolName = data == null ? null : String.valueOf(data.get("tool"));
        if (toolName == null || toolName.isBlank() || "null".equals(toolName)) {
            throw new IllegalStateException("TOOL_WAITING 缺少 tool: actionId=" + actionId);
        }

        /*
         * 先真正执行 APPROVE。
         */
        AgentStateTransitionResult result = handleCommand(taskId, runId, actionId, actionCommand.commandDesc_EN);

        /*
         * Command 成功后，再保存 Permission Rule。
         */
        PermissionRuleDTO rule = PermissionRuleDTO.builder()
                .toolName(toolName)
                .decision(PermissionDecisionEnum.ALLOW)
                .scope(PermissionScopeEnum.TOOL)
                .pattern(null)
                .build();

        try {
            permissionRuntimeStore.saveRule(runId, rule);
            log.info("人工 Permission 允许已缓存: runId={}, actionId={}, tool={}", runId, actionId, toolName);
        } catch (Exception e) {
            /*
             * Permission Cache 写失败不能让已经成功的 Tool
             * 变成一次“命令失败”。
             *
             * 最坏结果只是下一次再次询问。
             */
            log.error("Permission Cache 写入失败: runId={}, actionId={}, tool={}", runId, actionId, toolName, e);
        }
        return result;
    }

    @Override
    public void handleHeartbeatTimeout(String taskId, String runId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);

        if (task == null) {
            log.warn("Heartbeat timeout处理失败，Task不存在: taskId={}, runId={}", taskId, runId);
            return;
        }
        // 防止旧 Run 的 watchdog 影响当前 Task
        if (!runId.equals(task.getRunId())) {
            log.debug("忽略旧 Run 的 heartbeat timeout: taskId={}, runId={}, currentRunId={}", taskId, runId, task.getRunId());
            return;
        }

        AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

        // 当前状态本身不允许 heartbeat timeout
        if (!stateMachine.canTransition(currentState, AgentEventEnum.HEARTBEAT_TIMEOUT)) {
            return;
        }

        AgentTaskStatusEnum nextState = stateMachine.transition(currentState, AgentEventEnum.HEARTBEAT_TIMEOUT);


        TaskHeartbeatTimeoutUpdateDTO update = new TaskHeartbeatTimeoutUpdateDTO();
        update.setTaskId(taskId);
        update.setRunId(runId);
        update.setCurrentStatus(task.getStatus());
        update.setNextStatus(nextState.statusCode);
        update.setUpdatedAt(System.currentTimeMillis());
        update.setLastHeartbeatAt(task.getLastHeartbeatAt());
        int updated = agentTaskMapper.updateStatusByHeartbeatTimeout(update);
        if (updated == 0) {
            // 并发条件下已经被其他流程修改
            log.debug("Heartbeat timeout状态更新未生效，Task可能已被其他流程处理: taskId={}, runId={}", taskId, runId);
        }
    }

    private String buildCommandReason(AgentTaskStatusEnum currentState, AgentActionCommandEnum command, AgentTaskStatusEnum nextState, String actionId) {
        return String.format(
                "Agent Action Command: %s, actionId=%s, %s -> %s",
                command.commandDesc_EN, actionId, currentState.statusDesc_EN, nextState.statusDesc_EN
        );
    }
    private String buildRunCommandReason(AgentTaskStatusEnum currentState, AgentRunCommandEnum command, AgentTaskStatusEnum nextState, String actionId) {
        return String.format(
                "Agent Run Command: %s, actionId=%s, %s -> %s",
                command.commandDesc_EN, actionId, currentState.statusDesc_EN, nextState.statusDesc_EN
        );
    }

    /**
     * Agent Event -> Enum。
     */
    private AgentEventEnum parseEvent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (AgentEventEnum event : AgentEventEnum.values()) {
            if (event.eventDesc.equalsIgnoreCase(value)) {
                return event;
            }
        }

        return null;
    }

    private String buildReason(AgentTaskStatusEnum from, AgentEventEnum event, AgentTaskStatusEnum to) {
        return String.format("Agent Event %s: %s -> %s", event.eventDesc, from.statusDesc_EN, to.statusDesc_EN);
    }
}
