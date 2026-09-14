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
    private AgentActionService agentActionService;
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
        return TaskRunContext.builder().session(session).workspace(workspace).task(task).run(run).build();
    }

    @Override
    public List<TaskDetailVO> getTasksBySessionId(String sessionId) {
        List<AgentTaskDO> tasks = agentTaskMapper.selectBySessionId(sessionId);
        return tasks.stream().map(this::toTaskDetailVO).toList();
    }

    @Override
    public void updateTaskStatus(AgentMessageDTO agentMessageDTO) {
        if (agentMessageDTO.getParentAgent() != null && !agentMessageDTO.getParentAgent().isBlank()) {
            return;
        }
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByDesc(agentMessageDTO.getStatus());
        log.info("[TASK STATUS UPDATE] taskId={}, inputStatus={}, resolvedStatus={}", agentMessageDTO.getTaskId(), agentMessageDTO.getStatus(), status == null ? null : status.statusCode);
        if (status == null) {
            log.info("当前状态不在可维护状态中");
            return;
        }
        AgentTaskDO agentTaskDO = new AgentTaskDO();
        agentTaskDO.setTaskId(agentMessageDTO.getTaskId());
        agentTaskDO.setSessionId(agentMessageDTO.getSessionId());
        agentTaskDO.setStatus(status.statusCode);
        agentTaskDO.setUpdatedAt(agentMessageDTO.getTimestamp());
        int updated = agentTaskMapper.updateTask(agentTaskDO);

        log.info("[TASK STATUS UPDATED] taskId={}, status={}, rows={}", agentMessageDTO.getTaskId(), status.statusCode, updated);
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
        return events.stream().map(this::toEventVO).toList();
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

            AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
            if (task == null) {
                throw new IllegalStateException("Task 不存在: taskId=" + taskId);
            }

            AgentRunDO run = agentRunMapper.selectByRunId(runId);
            if (run == null) {
                throw new IllegalStateException("Run 不存在: runId=" + runId);
            }

            if (!runId.equals(task.getRunId())) {
                throw new IllegalStateException("runId 不是当前 Task 的最新 Run: " + "taskId=" + taskId + ", 当前=" + task.getRunId() + ", 接收=" + runId);
            }

            sessionIdHolder[0] = task.getSessionId();

            AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());
            AgentTaskStatusEnum nextState = stateMachine.transition(currentState, AgentRunCommandEnum.RESUME);

            AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
            updateMessageDTO.setTaskId(taskId);
            updateMessageDTO.setRunId(runId);
            updateMessageDTO.setStatus(nextState.statusDesc_EN);
            updateMessageDTO.setSessionId(task.getSessionId());
            updateTaskStatus(updateMessageDTO);

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

            log.info("Agent Runtime Resume 状态迁移: taskId={}, runId={}, {} -> {}, command=RESUME", taskId, runId, currentState.statusDesc_EN, nextState.statusDesc_EN);

            return AgentStateTransitionResult.builder().sessionId(task.getSessionId()).changed(currentState != nextState).fromStatus(currentState).toStatus(nextState).actionId(run.getActionId()).triggerType("COMMAND").trigger(AgentRunCommandEnum.RESUME.commandDesc_EN).reason(buildRunCommandReason(currentState, AgentRunCommandEnum.RESUME, nextState, run.getActionId())).build();
        });

        if (result == null) {
            throw new IllegalStateException("Agent Resume 状态迁移失败: taskId=" + taskId);
        }

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

        return TaskOperateVO.builder().message("任务已继续执行").build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskOperateVO retryTask(String taskId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在: " + taskId);
        }
        AgentSessionDO agentSessionDO = agentSessionMapper.selectBySessionId(task.getSessionId());
        String newRunId = UUID.randomUUID().toString();
        Integer maxAttempt = agentRunMapper.selectMaxAttemptByTaskId(taskId);
        int newAttempt = (maxAttempt == null ? 0 : maxAttempt) + 1;
        long now = System.currentTimeMillis();

        AgentRunDO run = new AgentRunDO();
        run.setRunId(newRunId);
        run.setTaskId(taskId);
        run.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        run.setSessionId(task.getSessionId());
        run.setAttempt(newAttempt);
        run.setCreatedAt(now);
        run.setStartedAt(now);
        agentRunMapper.insertAgentRun(run);

        AgentTaskDO update = new AgentTaskDO();
        update.setTaskId(taskId);
        update.setRunId(newRunId);
        update.setStatus(AgentTaskStatusEnum.AGENT_THINKING.statusCode);
        update.setUpdatedAt(System.currentTimeMillis());
        agentTaskMapper.updateTask(update);

        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setTaskId(taskId);
        msg.setSessionId(task.getSessionId());
        msg.setRunId(newRunId);
        msg.setWorkspaceId(agentSessionDO.getWorkspaceId());
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setVersion("1.0");
        msg.setTimestamp(now);
        msg.setType("AGENT_TASK");
        msg.setCommand(AgentRunCommandEnum.START.commandDesc_EN);
        msg.setQuestion(task.getQuestion());
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));

        return TaskOperateVO.builder().taskId(taskId).runId(newRunId).status(AgentTaskStatusEnum.AGENT_THINKING.statusCode).statusValue("CREATED").message("任务已重新执行").build();
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
        return tasks.stream().map(this::toTaskDetailVO).toList();
    }

    private AgentRunVO toAgentRunVO(AgentRunDO run, String currentRunId) {
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByCode(run.getStatus());
        return AgentRunVO.builder().runId(run.getRunId()).taskId(run.getTaskId()).sessionId(run.getSessionId()).attempt(run.getAttempt()).status(run.getStatus()).statusValue(status == null ? "UNKNOWN" : status.statusDesc_EN).startedAt(run.getStartedAt()).endedAt(run.getEndedAt()).errorMessage(run.getErrorMessage()).createdAt(run.getCreatedAt()).current(run.getRunId().equals(currentRunId)).build();
    }

    private TaskDetailVO toTaskDetailVO(AgentTaskDO task) {
        AgentTaskStatusEnum status = AgentTaskStatusEnum.getStatusByCode(task.getStatus());
        if (status == null) {
            throw new BusinessException("未知任务状态: " + task.getStatus());
        }
        return TaskDetailVO.builder().taskId(task.getTaskId()).sessionId(task.getSessionId()).runId(task.getRunId()).status(task.getStatus()).statusValue(status.statusDesc_EN).question(task.getQuestion()).createdAt(task.getCreatedAt()).updatedAt(task.getUpdatedAt()).build();
    }

    private TaskResultVO buildTaskResultVO(AgentTaskDO task, AgentRunDO run, AgentChatViewVO chat) {
        return TaskResultVO.builder().taskId(task.getTaskId()).runId(run.getRunId()).status(AgentTaskStatusEnum.getStatusByCode(run.getStatus()).statusDesc_EN).chat(chat).build();
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

        AgentEventEnum event = parseEvent(messageDTO.getEvent());
        if (event == null) {
            log.debug("忽略未知 Agent Event: taskId={}, runId={}, event={}", messageDTO.getTaskId(), messageDTO.getRunId(), messageDTO.getEvent());
            return null;
        }

        AgentTaskDO task = agentTaskMapper.selectByTaskId(messageDTO.getTaskId());
        if (task == null) {
            log.info("Task 不存在: {}", messageDTO.getTaskId());
            return null;
        }

        AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

        if (currentState == WAITING_HUMAN && event == TOOL_RESULT) {
            log.info("等待审批期间收到 TOOL_RESULT，视为取消收尾结果，不驱动状态迁移: taskId={}, runId={}", messageDTO.getTaskId(), messageDTO.getRunId());

            return AgentStateTransitionResult.builder().changed(false).fromStatus(currentState).toStatus(currentState).triggerType("EVENT").trigger(event.eventDesc).reason("等待审批期间的 Tool Result，仅用于闭合 Tool Call").build();
        }

        AgentTaskStatusEnum nextState = stateMachine.transition(currentState, event);
        log.info("[RUNTIME TRANSITION] taskId={}, runId={}, event={}, current={}, next={}, nextDesc={}, nextCode={}", messageDTO.getTaskId(), messageDTO.getRunId(), event.eventDesc, currentState.statusDesc_EN, nextState.statusDesc_EN, nextState.statusDesc_EN, nextState.statusCode);

        if (nextState == currentState) {
            return AgentStateTransitionResult.builder().changed(false).fromStatus(currentState).toStatus(currentState).triggerType("EVENT").trigger(event.eventDesc).reason("Agent Event 未导致 Runtime 状态变化").build();
        }

        AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
        updateMessageDTO.setTaskId(messageDTO.getTaskId());
        updateMessageDTO.setStatus(nextState.statusDesc_EN);
        updateMessageDTO.setSessionId(messageDTO.getSessionId());
        updateTaskStatus(updateMessageDTO);

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

        log.info("Agent Runtime 状态迁移: taskId={}, runId={}, {} -> {}, event={}", messageDTO.getTaskId(), messageDTO.getRunId(), currentState.statusDesc_EN, nextState.statusDesc_EN, event.eventDesc);

        return AgentStateTransitionResult.builder().changed(true).fromStatus(currentState).toStatus(nextState).triggerType("EVENT").trigger(event.eventDesc).reason(buildReason(currentState, event, nextState)).build();
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

        AgentActionCommandEnum actionCommand;
        try {
            actionCommand = AgentActionCommandEnum.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知 Agent Action Command: " + command, e);
        }

        AgentStateTransitionResult result = transactionTemplate.execute(status -> {
            AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
            AgentRunDO run = agentRunMapper.selectByRunId(runId);
            if (task == null || run == null) {
                throw new IllegalStateException("Task或Run 不存在: taskId:" + taskId + ", runId:" + runId);
            }

            if (!task.getRunId().equals(runId)) {
                throw new IllegalStateException("runId不是当前的最新: 当前:" + task.getRunId() + ", 接收:" + runId);
            }

            AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

            AgentTransitionContext context = AgentTransitionContext.builder().taskId(taskId).runId(runId).actionId(actionId).state(currentState).build();

            boolean allowed = switch (actionCommand) {
                case APPROVE -> agentTransitionGuard.canApprove(context, run.getActionId());
                case REJECT -> agentTransitionGuard.canReject(context, run.getActionId());
            };

            if (!allowed) {
                throw new IllegalStateException("当前状态不允许执行 Action Command: " + "taskId=" + taskId + ", runId=" + runId + ", actionId=" + actionId + ", command=" + actionCommand + ", currentState=" + currentState);
            }

            AgentTaskStatusEnum nextState = stateMachine.transition(currentState, actionCommand);

            AgentMessageDTO updateMessageDTO = new AgentMessageDTO();
            updateMessageDTO.setTaskId(taskId);
            updateMessageDTO.setRunId(runId);
            updateMessageDTO.setStatus(nextState.statusDesc_EN);
            updateTaskStatus(updateMessageDTO);

            AgentRunStateHistoryDO history = new AgentRunStateHistoryDO();
            history.setTaskId(taskId);
            history.setRunId(runId);
            history.setFromStatus(currentState.statusDesc_EN);
            history.setTriggerType("COMMAND");
            history.setTrigger(actionCommand.commandDesc_EN);
            history.setToStatus(nextState.statusDesc_EN);
            history.setMessageId(null);
            history.setStep(null);
            history.setReason(buildCommandReason(currentState, actionCommand, nextState, actionId));
            history.setCreatedAt(System.currentTimeMillis());
            stateHistoryMapper.insertStateHistory(history);

            log.info("Agent Runtime Command 状态迁移: taskId={}, runId={}, actionId={}, {} -> {}, command={}", taskId, runId, actionId, currentState.statusDesc_EN, nextState.statusDesc_EN, actionCommand.commandDesc_EN);

            return AgentStateTransitionResult.builder().changed(currentState != nextState).fromStatus(currentState).toStatus(nextState).actionId(run.getActionId()).triggerType("COMMAND").trigger(actionCommand.commandDesc_EN).reason(buildCommandReason(currentState, actionCommand, nextState, actionId)).build();
        });

        if (result == null) {
            throw new IllegalStateException("Agent Command 状态迁移失败: taskId=" + taskId);
        }

        sendCommand(taskId, runId, actionId, actionCommand.commandDesc_EN);
        return result;
    }

    /**
     * 只向 Python Runtime 发送 Action Command。
     *
     * 不修改 Task / Run 状态，不写 StateHistory。
     *
     * 用于 Child Agent 的 Tool Action。
     */
    @Override
    public void sendCommand(String taskId, String runId, String actionId, String command) {
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

        AgentActionCommandEnum actionCommand;
        try {
            actionCommand = AgentActionCommandEnum.valueOf(command.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知 Agent Action Command: " + command, e);
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

        log.info("发送 Agent Action Command: taskId={}, runId={}, actionId={}, command={}", taskId, runId, actionId, actionCommand.commandDesc_EN);
    }

    @Override
    public TaskOperateVO cancelTask(String taskId, String runId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        AgentRunDO run = agentRunMapper.selectByRunId(runId);
        if (task == null) {
            throw new IllegalStateException("Task 不存在: taskId=" + taskId);
        }
        if (run == null) {
            throw new IllegalStateException("Run 不存在: " + runId);
        }
        if (!runId.equals(task.getRunId())) {
            throw new IllegalStateException("runId 不是当前 Task 的最新 Run: " + "taskId=" + taskId + ", 当前=" + task.getRunId() + ", 接收=" + runId);
        }

        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setType("AGENT_TASK");
        agentTaskMessage.setCommand(AgentRunCommandEnum.CANCEL.commandDesc_EN);
        agentTaskMessage.setVersion("1.0");
        agentTaskMessage.setTaskId(taskId);
        agentTaskMessage.setRunId(runId);
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentTaskMessage));

        return TaskOperateVO.builder().message("任务已发送取消请求").build();
    }

    @Override
    public AgentStateTransitionResult handleUserCommand(String runId, String actionId, String command) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }

        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId 不能为空");
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
        if (agentRunDO == null) {
            throw new IllegalStateException("Run 不存在: " + runId);
        }

        String taskId = agentRunDO.getTaskId();

        AgentActionDO action = agentActionService.getByActionId(actionId);
        if (action == null) {
            throw new IllegalStateException("Action 不存在: actionId=" + actionId);
        }

        // 校验 Action 是否属于当前 Run
        if (action.getRunId() == null || !runId.equals(action.getRunId())) {
            throw new IllegalStateException("Action 不属于当前 Run: actionId=" + actionId + ", actionRunId=" + action.getRunId() + ", runId=" + runId);
        }

        boolean childAgent = action.getParentAgent() != null && !action.getParentAgent().isBlank();

        /*
         * Child Agent：
         * 不修改 Global Task / Run Runtime State。
         * 只操作 AgentAction，然后向 Python ExecutionGate 发送命令。
         */
        if (childAgent) {
            if (actionCommand == AgentActionCommandEnum.APPROVE) {
                agentActionService.approve(actionId);

                PermissionRuleDTO rule = PermissionRuleDTO.builder().toolName(action.getToolName()).decision(PermissionDecisionEnum.ALLOW).scope(PermissionScopeEnum.TOOL).pattern(null).build();

                try {
                    permissionRuntimeStore.saveRule(runId, rule);
                } catch (Exception e) {
                    log.error("Permission Cache 写入失败: runId={}, actionId={}, tool={}", runId, actionId, action.getToolName(), e);
                }

                sendCommand(taskId, runId, actionId, AgentActionCommandEnum.APPROVE.commandDesc_EN);

                return AgentStateTransitionResult.builder().changed(true).actionId(actionId).triggerType("ACTION").trigger(actionCommand.commandDesc_EN).reason("Child Agent Action APPROVE").build();
            }

            if (actionCommand == AgentActionCommandEnum.REJECT) {
                agentActionService.reject(actionId);

                sendCommand(taskId, runId, actionId, AgentActionCommandEnum.REJECT.commandDesc_EN);

                return AgentStateTransitionResult.builder().changed(true).actionId(actionId).triggerType("ACTION").trigger(actionCommand.commandDesc_EN).reason("Child Agent Action REJECT").build();
            }
        }

        /*
         * Root Agent：
         * 保持原来的 Global Task Runtime State 流程。
         */
        if (actionCommand == AgentActionCommandEnum.REJECT) {
            return handleCommand(taskId, runId, actionId, actionCommand.commandDesc_EN);
        }

        AgentEventDO waitingEvent = agentEventService.getToolWaitingByActionId(taskId, runId, actionId);

        if (waitingEvent == null) {
            throw new IllegalStateException("找不到对应的 TOOL_WAITING Event: " + "taskId=" + taskId + ", runId=" + runId + ", actionId=" + actionId);
        }

        AgentStateTransitionResult result = handleCommand(taskId, runId, actionId, actionCommand.commandDesc_EN);

        PermissionRuleDTO rule = PermissionRuleDTO.builder().toolName(action.getToolName()).decision(PermissionDecisionEnum.ALLOW).scope(PermissionScopeEnum.TOOL).pattern(null).build();

        try {
            permissionRuntimeStore.saveRule(runId, rule);

            log.info("Root Agent Permission 允许已缓存: runId={}, actionId={}, tool={}", runId, actionId, action.getToolName());
        } catch (Exception e) {
            log.error("Permission Cache 写入失败: runId={}, actionId={}, tool={}", runId, actionId, action.getToolName(), e);
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

        if (!runId.equals(task.getRunId())) {
            log.debug("忽略旧 Run 的 heartbeat timeout: taskId={}, runId={}, currentRunId={}", taskId, runId, task.getRunId());
            return;
        }

        AgentTaskStatusEnum currentState = AgentTaskStatusEnum.getStatusByCode(task.getStatus());

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
            log.debug("Heartbeat timeout状态更新未生效，Task可能已被其他流程处理: taskId={}, runId={}", taskId, runId);
        }
    }

    private String buildCommandReason(AgentTaskStatusEnum currentState, AgentActionCommandEnum command, AgentTaskStatusEnum nextState, String actionId) {
        return String.format("Agent Action Command: %s, actionId=%s, %s -> %s", command.commandDesc_EN, actionId, currentState.statusDesc_EN, nextState.statusDesc_EN);
    }

    private String buildRunCommandReason(AgentTaskStatusEnum currentState, AgentRunCommandEnum command, AgentTaskStatusEnum nextState, String actionId) {
        return String.format("Agent Run Command: %s, actionId=%s, %s -> %s", command.commandDesc_EN, actionId, currentState.statusDesc_EN, nextState.statusDesc_EN);
    }

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