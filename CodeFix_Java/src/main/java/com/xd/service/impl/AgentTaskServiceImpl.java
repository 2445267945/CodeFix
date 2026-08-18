package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.assembler.AgentChatBlockAssembler;
import com.xd.mapper.AgentEventMapper;
import com.xd.mapper.AgentRunMapper;
import com.xd.mapper.AgentTaskMapper;
import com.xd.mapper.ChatMessageMapper;
import com.xd.model.context.TaskRunContext;
import com.xd.model.dto.*;
import com.xd.model.entity.*;
import com.xd.model.enums.AgentCommandEnum;
import com.xd.model.enums.AuditTaskStatusEnum;
import com.xd.model.enums.ChatMessageRoleEnum;
import com.xd.model.vo.*;
import com.xd.mq.MQProducer;
import com.xd.service.*;
import com.xd.validator.JavaSyntaxValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    @Autowired
    private AgentTaskMapper agentTaskMapper;
    @Autowired
    private AgentRunService agentRunService;
    @Autowired
    private CodeParserService parserService;
    @Autowired
    private JavaSyntaxValidator validator;
    @Autowired
    private AgentEventMapper agentEventMapper;
    @Autowired
    private AgentRunMapper agentRunMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private AgentEventService agentEventService;
    @Autowired
    private AgentChatAssemblerService agentChatAssemblerService;
    @Autowired
    private AgentChatBlockAssembler agentChatBlockAssembler;
    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskRunContext createTaskWithRun(String sessionId, String question) {

        String runId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        // 1. 获取 / 创建 Session
        AgentSessionDO session = agentSessionService.getOrCreateSession(sessionId, question);

        String actualSessionId = session.getSessionId();

        // 2. 获取 / 创建 Workspace
        WorkspaceDO workspace = workspaceService.getOrCreateWorkspace(actualSessionId);

        // 3. 确保 Session 绑定 Workspace
        if (session.getWorkspaceId() == null || !workspace.getWorkspaceId().equals(session.getWorkspaceId())) {
            agentSessionService.bindWorkspace(actualSessionId, workspace.getWorkspaceId());
            session.setWorkspaceId(workspace.getWorkspaceId());
        }

        // 4. 创建 Task
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskId(taskId);
        task.setRunId(runId);
        task.setSessionId(actualSessionId);
        task.setQuestion(question);
        task.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion("1.0");

        // 5. 创建 Run
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(actualSessionId);
        run.setAttempt(1);
        run.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
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
        AuditTaskStatusEnum status = AuditTaskStatusEnum.getStatusByDesc(agentMessageDTO.getStatus());
        if (status == null) {
            log.info("当前状态不在可维护状态中");
            return;
        }
        AgentTaskDO agentTaskDO = new AgentTaskDO();
        agentTaskDO.setTaskId(agentMessageDTO.getTaskId());
        agentTaskDO.setSessionId(agentMessageDTO.getSessionId());
        agentTaskDO.setStatus(AuditTaskStatusEnum.getStatusByDesc(agentMessageDTO.getStatus()).statusCode);
        agentTaskDO.setUpdatedAt(agentMessageDTO.getTimestamp());
        agentTaskMapper.updateTask(agentTaskDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO createTask(AuditTaskCreateDTO request) {
        long now = System.currentTimeMillis();
        // 1. 获取 / 创建 Session
        AgentSessionDO session = agentSessionService.getOrCreateSession(request.getSessionId(), request.getQuestion());
        String sessionId = session.getSessionId();

        // 2.创建工作目录
        WorkspaceDO workspace = workspaceService.getOrCreateWorkspace(sessionId);

        if (session.getWorkspaceId() == null || !workspace.getWorkspaceId().equals(session.getWorkspaceId())) {
            agentSessionService.bindWorkspace(sessionId, workspace.getWorkspaceId());
            session.setWorkspaceId(workspace.getWorkspaceId());
        }

        // 3. 生成 Task / Run
        String taskId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();

        // 4. 原始代码
        String originalCode = request.getCode();

        // 5. 语法检查
        Optional<String> syntaxError = validator.validate(originalCode);

        List<CodeSmellDTO> smells;
        String question;

        if (syntaxError.isPresent()) {
            smells = null;
            question = buildSyntaxErrorQuestion(request.getQuestion(), originalCode, syntaxError.get());
        } else {
            // 如果前端没有传 smells，则使用后端预扫描结果
            smells = request.getSmells() != null ? request.getSmells() : parserService.extractSmells(originalCode);
            question = buildTaskQuestion(request.getQuestion(), originalCode, smells);
        }

        // 6. 创建 Task
        AgentTaskDO task = new AgentTaskDO();
        task.setTaskId(taskId);
        task.setSessionId(sessionId);
        task.setRunId(runId);
        task.setQuestion(question);
        task.setCode(originalCode);
        task.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setVersion("1.0");
        agentTaskMapper.insertAgentTask(task);

        // 7. 创建 Run
        AgentRunDO run = new AgentRunDO();
        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(sessionId);
        run.setAttempt(1);
        run.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        agentRunMapper.insertAgentRun(run);

        // 8. 构造 Python 消息
        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setVersion("1.0");
        msg.setTimestamp(now);
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setTaskId(taskId);
        msg.setSessionId(sessionId);
        msg.setRunId(runId);
        msg.setType("AGENT_TASK");
        msg.setCommand(AgentCommandEnum.START.commandDesc_EN);
        msg.setQuestion(question);
        msg.setCode(originalCode);
        msg.setSmells(smells);

        // 9. 保存第一轮 USER ChatMessage
        ChatMessageDO message = new ChatMessageDO();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole("USER");
        message.setContent(request.getQuestion() + request.getCode());
        message.setCreatedAt(now);
        chatMessageMapper.insertChatMessage(message);

        // 10. MQ
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));

        // 11. 返回
        return TaskCreateVO.builder().taskId(taskId).sessionId(sessionId).runId(runId).status(AuditTaskStatusEnum.QUEUED.statusCode).build();
    }

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
        AgentTaskDO agentTaskDO = agentTaskMapper.selectByTaskId(taskId);
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setSessionId(agentTaskDO.getSessionId());
        agentTaskMessage.setRunId(agentTaskDO.getRunId());
        agentTaskMessage.setType("AGENT_TASK");
        agentTaskMessage.setCommand(AgentCommandEnum.RESUME.commandDesc_EN);
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
        run.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
        run.setCreatedAt(now);
        run.setStartedAt(now);
        agentRunMapper.insertAgentRun(run);

        // 5. 更新 Task 当前 Run
        AgentTaskDO update = new AgentTaskDO();
        update.setTaskId(taskId);
        update.setRunId(newRunId);
        update.setStatus(AuditTaskStatusEnum.QUEUED.statusCode);
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
        msg.setCommand(AgentCommandEnum.START.commandDesc_EN);
        msg.setQuestion(task.getQuestion());
        msg.setCode(task.getCode());
        // 7. 投递 MQ
        mqProducer.send("agent_task_topic", "*", JSON.toJSONString(msg));
        // 8. 返回
        return TaskOperateVO.builder().taskId(taskId).runId(newRunId).status(AuditTaskStatusEnum.QUEUED.statusCode).statusValue("QUEUED").message("任务已重新执行").build();
    }

    @Override
    public TaskOperateVO cancelTask(String taskId, String runId) {
        AgentTaskMessage agentTaskMessage = new AgentTaskMessage();
        agentTaskMessage.setType("AGENT_TASK");   // 与 Python MESSAGE_TYPE_MAP 一致
        agentTaskMessage.setCommand(AgentCommandEnum.CANCEL.commandDesc_EN);
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

        return tasks.stream().map(this::toTaskDetailVO).toList();
    }


    private AgentRunVO toAgentRunVO(AgentRunDO run, String currentRunId) {
        AuditTaskStatusEnum status = AuditTaskStatusEnum.getStatusByCode(run.getStatus());

        return AgentRunVO.builder().runId(run.getRunId()).taskId(run.getTaskId()).sessionId(run.getSessionId()).attempt(run.getAttempt()).status(run.getStatus()).statusValue(status == null ? "UNKNOWN" : status.statusDesc_EN).startedAt(run.getStartedAt()).endedAt(run.getEndedAt()).errorMessage(run.getErrorMessage()).createdAt(run.getCreatedAt()).current(run.getRunId().equals(currentRunId)).build();
    }

    private TaskDetailVO toTaskDetailVO(AgentTaskDO task) {

        AuditTaskStatusEnum status = AuditTaskStatusEnum.getStatusByCode(task.getStatus());

        if (status == null) {
            throw new RuntimeException("未知任务状态: " + task.getStatus());
        }

        return TaskDetailVO.builder().taskId(task.getTaskId()).sessionId(task.getSessionId()).runId(task.getRunId()).status(task.getStatus()).statusValue(status.statusDesc_EN).question(task.getQuestion()).createdAt(task.getCreatedAt()).updatedAt(task.getUpdatedAt()).build();
    }

    private TaskResultVO buildTaskResultVO(AgentTaskDO task, AgentRunDO run, AgentChatViewVO chat) {

        return TaskResultVO.builder().taskId(task.getTaskId()).runId(run.getRunId()).status(AuditTaskStatusEnum.getStatusByCode(run.getStatus()).statusDesc_EN).chat(chat).build();
    }
}
