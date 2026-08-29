package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.*;

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
        String workspaceName = request.getWorkspaceName();
        // 1. 获取 / 创建 Session
        AgentSessionDO session = agentSessionService.getOrCreateSession(sessionId, question);

        String actualSessionId = session.getSessionId();
        String workspaceId = session.getWorkspaceId();
        WorkspaceDO workspace;
        if (workspaceId == null || workspaceId.isEmpty()) {
            // 2. 获取 / 创建 Workspace
            workspace = workspaceService.createWorkspace(workspaceName);
            // 3. 确保 Session 绑定 Workspace
            agentSessionService.bindWorkspace(actualSessionId, workspace.getWorkspaceId());
            session.setWorkspaceId(workspace.getWorkspaceId());
        } else {
            workspace = workspaceService.getWorkspace(workspaceId);
        }

        // 4. 创建 Task
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskId(taskId);
        task.setRunId(runId);
        task.setSessionId(actualSessionId);
        task.setQuestion(question);
        task.setStatus(AgentTaskStatusEnum.CREATED.statusCode);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion("1.0");

        // 5. 创建 Run
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(actualSessionId);
        run.setAttempt(1);
        run.setStatus(AgentTaskStatusEnum.QUEUED.statusCode);
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

//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public TaskCreateVO createTask(AuditTaskCreateDTO request) {
//        long now = System.currentTimeMillis();
//        // 1. 获取 / 创建 Session
//        AgentSessionDO session = agentSessionService.getOrCreateSession(request.getSessionId(), request.getQuestion());
//        String sessionId = session.getSessionId();
//
//        // 2.创建工作目录
//        WorkspaceDO workspace = workspaceService.getOrCreateWorkspace(sessionId);
//
//        if (session.getWorkspaceId() == null || !workspace.getWorkspaceId().equals(session.getWorkspaceId())) {
//            agentSessionService.bindWorkspace(sessionId, workspace.getWorkspaceId());
//            session.setWorkspaceId(workspace.getWorkspaceId());
//        }
//
//        // 3. 生成 Task / Run
//        String taskId = UUID.randomUUID().toString();
//        String runId = UUID.randomUUID().toString();
//
//        // 4. 原始代码
//        String originalCode = request.getCode();
//
//        // 5. 语法检查
//        Optional<String> syntaxError = validator.validate(originalCode);
//
//        List<CodeSmellDTO> smells;
//        String question;
//
//        if (syntaxError.isPresent()) {
//            smells = null;
//            question = buildSyntaxErrorQuestion(request.getQuestion(), originalCode, syntaxError.get());
//        } else {
//            // 如果前端没有传 smells，则使用后端预扫描结果
//            smells = request.getSmells() != null ? request.getSmells() : parserService.extractSmells(originalCode);
//            question = buildTaskQuestion(request.getQuestion(), originalCode, smells);
//        }
//
//        // 6. 创建 Task
//        AgentTaskDO task = new AgentTaskDO();
//        task.setTaskId(taskId);
//        task.setSessionId(sessionId);
//        task.setRunId(runId);
//        task.setQuestion(question);
//        task.setCode(originalCode);
//        task.setStatus(AgentTaskStatusEnum.QUEUED.statusCode);
//        task.setCreatedAt(now);
//        task.setUpdatedAt(now);
//        task.setVersion("1.0");
//        agentTaskMapper.insertAgentTask(task);
//
//        // 7. 创建 Run
//        AgentRunDO run = new AgentRunDO();
//        run.setRunId(runId);
//        run.setTaskId(taskId);
//        run.setSessionId(sessionId);
//        run.setAttempt(1);
//        run.setStatus(AgentTaskStatusEnum.QUEUED.statusCode);
//        run.setStartedAt(now);
//        run.setCreatedAt(now);
//        agentRunMapper.insertAgentRun(run);
//
//        // 8. 构造 Python 消息
//        AgentTaskMessage msg = new AgentTaskMessage();
//        msg.setVersion("1.0");
//        msg.setTimestamp(now);
//        msg.setMessageId(UUID.randomUUID().toString());
//        msg.setTaskId(taskId);
//        msg.setSessionId(sessionId);
//        msg.setRunId(runId);
//        msg.setType("AGENT_TASK");
//        msg.setCommand(AgentCommandEnum.START.commandDesc_EN);
//        msg.setQuestion(question);
//        msg.setCode(originalCode);
//        msg.setSmells(smells);
//
//        // 9. 保存第一轮 USER ChatMessage
//        ChatMessageDO message = new ChatMessageDO();
//        message.setMessageId(UUID.randomUUID().toString());
//        message.setSessionId(sessionId);
//        message.setRole("USER");
//        message.setContent(request.getQuestion() + request.getCode());
//        message.setCreatedAt(now);
//        chatMessageMapper.insertChatMessage(message);
//
//        // 10. MQ
//        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));
//
//        // 11. 返回
//        return TaskCreateVO.builder()
//                .taskId(taskId)
//                .sessionId(sessionId)
//                .runId(runId)
//                .status(AgentTaskStatusEnum.QUEUED.statusCode)
//                .build();
//    }

    /**
     * question 给人读；smells 给程序用
     */
    private String buildTaskQuestion(String userQuestion, String code, List<CodeSmellDTO> smells) {
        StringBuilder sb = new StringBuilder();
        // 用户明确提出的任务目标
        if (userQuestion != null && !userQuestion.isBlank()) {
            sb.append("用户任务：\n").append(userQuestion).append("\n\n");
        } else {
            sb.append("请审计并修复以下 Java 代码。\n\n");
        }
        // 当前代码
        sb.append("代码：\n").append(code).append("\n\n");
        // Java 预扫描结果
        if (smells != null && !smells.isEmpty()) {
            sb.append("预扫描嫌疑点（仅供参考）：\n");
            for (CodeSmellDTO smell : smells) {
                sb.append("- [行").append(smell.getLineNumber()).append("] ").append(smell.getType()).append(": ").append(smell.getDescription()).append("\n");
            }
        } else {
            sb.append("预扫描未发现明确嫌疑点，请进行常规规范与隐患检查。\n");
        }

        return sb.toString();
    }

    private String buildSyntaxErrorQuestion(String userQuestion, String code, String syntaxError) {
        StringBuilder sb = new StringBuilder();
        if (userQuestion != null && !userQuestion.isBlank()) {
            sb.append("用户任务：\n").append(userQuestion).append("\n\n");
        }
        sb.append("以下 Java 代码存在语法错误。\n").append("错误信息：\n").append(syntaxError).append("\n\n").append("请直接修复语法错误，无需关注其他设计问题。\n\n").append("代码：\n").append(code);
        return sb.toString();
    }

    @Override
    public TaskDetailVO getTask(String taskId) {
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
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
        AgentTaskDO agentTaskDO = agentTaskMapper.selectByTaskId(taskId);
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setSessionId(agentTaskDO.getSessionId());
        agentTaskMessage.setRunId(agentTaskDO.getRunId());
        agentTaskMessage.setType("AGENT_TASK");
        agentTaskMessage.setCommand(AgentRunCommandEnum.RESUME.commandDesc_EN);
        agentTaskMessage.setVersion("1.0");
        agentTaskMessage.setTaskId(taskId);
        agentTaskMessage.setRunId(agentTaskDO.getRunId());
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentTaskMessage));
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskOperateVO retryTask(String taskId) {
        // 1. 查询原任务
        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }
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
        run.setSessionId(task.getSessionId());
        run.setAttempt(newAttempt);
        run.setStatus(AgentTaskStatusEnum.QUEUED.statusCode);
        run.setCreatedAt(now);
        run.setStartedAt(now);
        agentRunMapper.insertAgentRun(run);

        // 5. 更新 Task 当前 Run
        AgentTaskDO update = new AgentTaskDO();
        update.setTaskId(taskId);
        update.setRunId(newRunId);
        update.setStatus(AgentTaskStatusEnum.QUEUED.statusCode);
        update.setUpdatedAt(System.currentTimeMillis());
        agentTaskMapper.updateTask(update);

        // 6. 构造新的 AgentTaskMessage
        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setTaskId(taskId);
        msg.setSessionId(task.getSessionId());
        msg.setRunId(newRunId);
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setVersion("1.0");
        msg.setTimestamp(now);
        msg.setType("AGENT_TASK");
        // Retry = 创建新Run，然后按照START执行
        msg.setCommand(AgentRunCommandEnum.START.commandDesc_EN);
        msg.setQuestion(task.getQuestion());
        msg.setCode(task.getCode());
        // 7. 投递 MQ
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));
        // 8. 返回
        return TaskOperateVO.builder()
                .taskId(taskId)
                .runId(newRunId)
                .status(AgentTaskStatusEnum.QUEUED.statusCode)
                .statusValue("QUEUED")
                .message("任务已重新执行")
                .build();
    }

    @Override
    public TaskOperateVO cancelTask(String taskId, String runId) {
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setType("AGENT_TASK");   // 与 Python MESSAGE_TYPE_MAP 一致
        agentTaskMessage.setCommand(AgentRunCommandEnum.CANCEL.commandDesc_EN);
        agentTaskMessage.setVersion("1.0");
        agentTaskMessage.setTaskId(taskId);
        agentTaskMessage.setRunId(runId);
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(agentTaskMessage));
        return null;
    }

    @Override
    public TaskResultVO getTaskResult(String taskId, String runId) {

        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);

        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
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
            throw new RuntimeException("未知任务状态: " + task.getStatus());
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
    public AgentStateTransitionResult handleUserCommand(String taskId, String runId, String actionId, String command) {
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

    private String buildCommandReason(AgentTaskStatusEnum currentState, AgentActionCommandEnum command,
                                      AgentTaskStatusEnum nextState, String actionId) {
        return String.format(
                "Agent Action Command: %s, actionId=%s, %s -> %s",
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
