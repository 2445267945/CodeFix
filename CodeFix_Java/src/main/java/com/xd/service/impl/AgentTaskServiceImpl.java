package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.convert.AIContentConvert;
import com.xd.mapper.AgentEventMapper;
import com.xd.mapper.AgentRunMapper;
import com.xd.mapper.AgentTaskMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.AgentTaskMessage;
import com.xd.model.dto.AuditTaskCreateDTO;
import com.xd.model.dto.CodeSmellDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.model.entity.TaskResultDO;
import com.xd.model.enums.AgentCommandEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.AuditTaskStatusEnum;
import com.xd.model.vo.*;
import com.xd.mq.MQProducer;
import com.xd.service.AgentEventService;
import com.xd.service.AgentRunService;
import com.xd.service.AgentTaskService;
import com.xd.validator.JavaSyntaxValidator;
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
    private CodeParserService parserService;
    @Autowired
    private JavaSyntaxValidator validator;
    @Autowired
    private AgentEventMapper agentEventMapper;
    @Autowired
    private AgentRunMapper agentRunMapper;
    @Autowired
    private MQProducer mqProducer;

    @Autowired
    private ObjectMapper objectMapper;

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
    public void insertAgentTask(AgentTaskMessage agentTaskMessage) {
        AgentTaskDO agentTaskDO = new AgentTaskDO();
        agentTaskDO.setTaskId(agentTaskMessage.getTaskId());
        agentTaskDO.setCode(agentTaskMessage.getCode());
        agentTaskDO.setQuestion(agentTaskMessage.getQuestion());
        agentTaskDO.setSessionId(agentTaskMessage.getSessionId());
        agentTaskDO.setRunId(agentTaskMessage.getRunId());
        agentTaskDO.setStatus(AuditTaskStatusEnum.AGENT_THINKING.statusCode);
        agentTaskDO.setCreatedAt(System.currentTimeMillis());
        agentTaskDO.setUpdatedAt(System.currentTimeMillis());
        agentTaskDO.setVersion("1.0");
        try {
            agentTaskDO.setSmells(objectMapper.writeValueAsString(agentTaskMessage.getSmells()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        agentTaskMapper.insertAgentTask(agentTaskDO);
        agentRunService.createFirstRun(agentTaskDO.getRunId(), agentTaskDO.getTaskId(), agentTaskDO.getSessionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO createTask(AuditTaskCreateDTO request) {
        String taskId = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String originalCode = request.getCode();
        // 1. Java语法检查
        Optional<String> syntaxError = validator.validate(originalCode);
        List<CodeSmellDTO> smells;
        String question;
        if (syntaxError.isPresent()) {
            smells = null;
            question =
                    "以下 Java 代码存在语法错误，错误信息如下：\n"
                            + syntaxError.get()
                            + "\n请直接修复语法错误，无需关注设计规范。\n"
                            + "代码：\n"
                            + originalCode;
        } else {
            // 2. 预扫描
            smells = parserService.extractSmells(originalCode);
            question = buildQuestionWithSmells(
                    originalCode,
                    smells
            );
        }
        // 3. 创建 Task
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

        // 4. 创建 Run
        agentRunService.createFirstRun(runId,  taskId,  sessionId);
        // 5. 构造 Python Agent 消息
        AgentTaskMessage msg = new AgentTaskMessage();
        msg.setVersion("1.0");
        msg.setTimestamp(now);
        msg.setTaskId(taskId);
        msg.setSessionId(sessionId);
        msg.setRunId(runId);
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setType("AGENT_TASK");
        msg.setCommand(AgentCommandEnum.START.commandDesc_EN);
        msg.setCode(originalCode);
        msg.setQuestion(question);
        msg.setSmells(smells);
        // 6. 发 MQ
        mqProducer.send( "agent_task_topic",  "*",  JSON.toJSONString(msg));
        return TaskCreateVO.builder()
                .taskId(taskId)
                .sessionId(sessionId)
                .runId(runId)
                .status(AuditTaskStatusEnum.QUEUED.statusCode)
                .build();
    }

    /**
     * question 给人读；smells 给程序用
     */
    private String buildQuestionWithSmells(String code, List<CodeSmellDTO> smells) {
        StringBuilder sb = new StringBuilder();
        sb.append("请审计并修复以下 Java 代码中的问题。\n");
        sb.append("代码：\n").append(code).append("\n");
        if (smells != null && !smells.isEmpty()) {
            sb.append("预扫描嫌疑点（仅供参考）：\n");
            for (CodeSmellDTO s : smells) {
                sb.append("- [行").append(s.getLineNumber()).append("] ")
                .append(s.getType()).append(": ")
                .append(s.getDescription()).append("\n"); } }
        else {
            sb.append("预扫描未发现明确嫌疑点，请做常规规范与隐患检查。\n");
        }
        return sb.toString();
    }

    @Override
    public TaskDetailVO getTask(String taskId) {

        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }
        return TaskDetailVO.builder()
                .taskId(task.getTaskId())
                .sessionId(task.getSessionId())
                .runId(task.getRunId())
                .status(task.getStatus())
                .statusValue(AuditTaskStatusEnum.getStatusByCode(task.getStatus()).statusDesc_EN)
                .question(task.getQuestion())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    @Override
    public List<AgentEventVO> getTaskEvents(String taskId, String runId) {
        List<AgentEventDO> events;
        if (runId == null || runId.isBlank()) {
            events = agentEventMapper.selectByTaskId(taskId);
        } else {
            events = agentEventMapper.selectByTaskIdAndRunId(taskId,  runId);
        }
        return events.stream()
                .map(this::toEventVO)
                .toList();
    }

    private AgentEventVO toEventVO(AgentEventDO eventDO) {
        AgentEventVO vo = new AgentEventVO();
        vo.setMessageId(eventDO.getMessageId());
        vo.setTaskId(eventDO.getTaskId());
        vo.setRunId(eventDO.getRunId());
        vo.setSessionId(eventDO.getSessionId());
        vo.setAgentName(eventDO.getAgentName());
        vo.setParentAgent(eventDO.getParentAgent());
        vo.setEvent(eventDO.getEvent());
        vo.setStep(eventDO.getStep());
        vo.setStatus(eventDO.getStatus());
        vo.setTimestamp(eventDO.getEventTimestamp());
        // DO 中是 JSON 字符串，VO 中转换成 Map
        if (eventDO.getOutput() != null && !eventDO.getOutput().isBlank()) {
            try {
                vo.setOutput(JSON.parseObject(eventDO.getOutput(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                log.warn("AgentEvent output JSON解析失败, messageId={}",  eventDO.getMessageId(),  e);
                // 保证查询接口不会因为单条脏数据整体失败
                vo.setOutput(Map.of("output", eventDO.getOutput()));
            }
        } else {
            vo.setOutput(Collections.emptyMap());
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
        mqProducer.send("agent_task_topic", "*",JSON.toJSONString(msg));
        // 8. 返回
        return TaskOperateVO.builder()
                .taskId(taskId)
                .runId(newRunId)
                .status(AuditTaskStatusEnum.QUEUED.statusCode)
                .statusValue("QUEUED")
                .message("任务已重新执行")
                .build();
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
    public TaskResultVO getTaskResult(String taskId) {
        TaskResultDO result = agentTaskMapper.selectTaskResult(taskId);
        if (result == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }
        return buildTaskResultVO(result);
    }

    private TaskResultVO buildTaskResultVO(TaskResultDO result) {
        TaskResultVO.TaskResultVOBuilder builder = TaskResultVO.builder()
                .taskId(result.getTaskId())
                .runId(result.getRunId())
                .status(AuditTaskStatusEnum.getStatusByCode(result.getTaskStatus()).statusDesc_EN);
        // 当前 Run 还没有 FINISH Event
        if (result.getOutput() == null || result.getOutput().isBlank()) {
            return builder.build();
        }
        try {
            Map<String, Object> output = JSON.parseObject(result.getOutput());
            Object answerObj = output.get("answer");
            if (!(answerObj instanceof Map)) {
                log.warn("FINISH事件缺少answer字段: taskId={}, runId={}", result.getTaskId(), result.getRunId());
                return builder.build();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> answer = (Map<String, Object>) answerObj;
            return builder
                    .code(toStringValue(answer.get("code")))
                    .changes(toStringValue(answer.get("changes")))
                    .build();
        } catch (Exception e) {
            log.error("解析任务最终结果失败: taskId={}, runId={}", result.getTaskId(), result.getRunId(), e);
            throw new RuntimeException("任务最终结果解析失败", e);
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
