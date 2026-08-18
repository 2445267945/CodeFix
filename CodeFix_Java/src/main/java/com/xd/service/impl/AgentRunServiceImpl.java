package com.xd.service.impl;

import com.xd.mapper.AgentRunMapper;
import com.xd.mapper.AgentTaskMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.model.enums.AuditTaskStatusEnum;
import com.xd.model.vo.AgentRunVO;
import com.xd.service.AgentRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class AgentRunServiceImpl implements AgentRunService {

    @Autowired
    private AgentRunMapper agentRunMapper;
    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Override
    public AgentRunDO createRun(String runId, String taskId, String sessionId, Integer status) {

        long now = System.currentTimeMillis();

        Integer maxAttempt = agentRunMapper.selectMaxAttemptByTaskId(taskId);

        int attempt = (maxAttempt == null ? 0 : maxAttempt) + 1;

        AgentRunDO run = new AgentRunDO();

        run.setRunId(runId);
        run.setTaskId(taskId);
        run.setSessionId(sessionId);
        run.setAttempt(attempt);
        run.setStatus(status);
        run.setCreatedAt(now);

        // QUEUED 时还没真正开始执行
        run.setStartedAt(null);
        run.setEndedAt(null);

        agentRunMapper.insertAgentRun(run);

        return run;
    }

    @Override
    public void updateRun(AgentMessageDTO messageDTO) {

        // 子 Agent 不允许修改整个 Run 状态
        if (messageDTO.getParentAgent() != null && !messageDTO.getParentAgent().isBlank()) {
            return;
        }
        AuditTaskStatusEnum status = AuditTaskStatusEnum.getStatusByDesc(messageDTO.getStatus());
        if (status == null) {
            log.warn("无法更新Run状态: taskId={}, runId={}, status={}", messageDTO.getTaskId(), messageDTO.getRunId(), messageDTO.getStatus());
            return;
        }
        AgentRunDO update = new AgentRunDO();
        update.setRunId(messageDTO.getRunId());
        update.setStatus(status.statusCode);

        /*
         * 后续可以根据状态补充：
         *
         * THINKING / EXECUTING
         *     → startedAt
         *
         * FINISHED / ERROR / CANCELLED
         *     → endedAt
         *
         * ERROR
         *     → errorMessage
         */

        agentRunMapper.updateRun(update);
    }

    @Override
    public List<AgentRunVO> getRuns(String taskId) {

        AgentTaskDO task = agentTaskMapper.selectByTaskId(taskId);

        if (task == null) {
            throw new RuntimeException("任务不存在: " + taskId);
        }

        List<AgentRunDO> runs = agentRunMapper.selectByTaskId(taskId);

        if (runs == null || runs.isEmpty()) {
            return Collections.emptyList();
        }

        String currentRunId = task.getRunId();

        return runs.stream().map(run -> toRunVO(run, currentRunId)).toList();
    }

    private AgentRunVO toRunVO(AgentRunDO run, String currentRunId) {

        return AgentRunVO.builder().runId(run.getRunId()).taskId(run.getTaskId()).sessionId(run.getSessionId()).attempt(run.getAttempt()).status(run.getStatus()).statusValue(resolveStatusValue(run.getStatus())).startedAt(run.getStartedAt()).endedAt(run.getEndedAt()).errorMessage(run.getErrorMessage()).createdAt(run.getCreatedAt()).current(Objects.equals(run.getRunId(), currentRunId)).build();
    }

    private String resolveStatusValue(Integer status) {
        AuditTaskStatusEnum statusEnum = AuditTaskStatusEnum.getStatusByCode(status);
        return statusEnum == null ? "" : statusEnum.statusDesc_EN;
    }

    @Override
    public AgentRunDO getRun(String taskId, String runId) {

        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId不能为空");
        }

        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId不能为空");
        }

        AgentRunDO run = agentRunMapper.selectByTaskIdAndRunId(taskId, runId);

        if (run == null) {
            throw new RuntimeException("Run不存在: taskId=" + taskId + ", runId=" + runId);
        }

        return run;
    }
}