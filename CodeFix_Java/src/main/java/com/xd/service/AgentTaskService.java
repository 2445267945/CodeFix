package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.dto.AgentTaskMessage;
import com.xd.model.dto.AuditTaskCreateDTO;
import com.xd.model.vo.*;
import org.springframework.stereotype.Service;

import java.util.List;

public interface AgentTaskService {

    public void updateTaskStatus(AgentMessageDTO agentMessageDTO);

    public void insertAgentTask(AgentTaskMessage agentTaskMessage);

    TaskCreateVO createTask(AuditTaskCreateDTO request);

    TaskDetailVO getTask(String taskId);

    List<AgentEventVO> getTaskEvents(String taskId, String runId);

    TaskOperateVO resumeTask(String taskId, String runId);

    TaskOperateVO retryTask(String taskId);

    TaskOperateVO cancelTask(String taskId, String runId);

    TaskResultVO getTaskResult(String taskId);
}
