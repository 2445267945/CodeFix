package com.xd.service;

import com.xd.model.context.TaskRunContext;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.*;
import com.xd.state.AgentStateTransitionResult;

import java.util.List;

public interface AgentTaskService {

    /**
     * 获取session,创建一个 Task，并创建该 Task 的第一条 Run
     */
    TaskRunContext createTaskWithRun(String sessionId, String question, String workspaceName);

    /**
     * 创建 Task 的第一阶段入口
     *
     * 新建审计任务时使用
     */
//    TaskCreateVO createTask(AuditTaskCreateDTO request);

    /**
     * 查询全部 Task
     */
    List<TaskDetailVO> getTasks();

    /**
     * 查询某个 Session 下的 Task
     */
    List<TaskDetailVO> getTasksBySessionId(String sessionId);

    /**
     * 查询单个 Task
     */
    TaskDetailVO getTask(String taskId);

    /**
     * 查询 Task 的 Event
     * <p>
     * runId 为空时查询当前 Run
     */
    List<AgentEventVO> getTaskEvents(String taskId, String runId);

    /**
     * 更新 Task 状态
     * <p>
     * Python → Java Agent Event
     */
    void updateTaskStatus(AgentMessageDTO agentMessageDTO);

    /**
     * Resume 当前 Run
     */
    TaskOperateVO resumeTask(String taskId, String runId);

    /**
     * Retry，创建新的 Run
     */
    TaskOperateVO retryTask(String taskId);

    /**
     * Cancel 当前 Run
     */
    TaskOperateVO cancelTask(String taskId, String runId);

    /**
     * 查询 Task Result
     * <p>
     * runId 为空时查询当前 Run
     */
    TaskResultVO getTaskResult(String taskId, String runId);

    AgentStateTransitionResult handleAgentEvent(AgentMessageDTO message);

    // Command
    AgentStateTransitionResult handleCommand(String taskId, String runId, String actionId, String command);
}
