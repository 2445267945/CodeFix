package com.xd.controller;

import com.xd.controller.request.AgentCommandRequest;
import com.xd.controller.response.Result;
import com.xd.model.vo.*;
import com.xd.service.AgentRunService;
import com.xd.service.AgentSseService;
import com.xd.service.AgentTaskService;
import com.xd.runtime.state.AgentStateTransitionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentSseService agentSseService;
    @Autowired
    private AgentRunService agentRunService;

    @PostMapping("/command")
    public Result<AgentStateTransitionResult> handleCommand(@RequestBody AgentCommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Command 请求不能为空");
        }
        AgentStateTransitionResult result = agentTaskService.handleUserCommand(request.getRunId(), request.getActionId(), request.getCommand());
        return Result.success(result);
    }

    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> getTask(@PathVariable String taskId) {
        TaskDetailVO task = agentTaskService.getTask(taskId);
        return Result.success(task);
    }

    /**
     * 建立 Task 的实时事件流
     * 前端建立连接后，Java 会持续向该连接推送：
     * - Agent THINK
     * - TOOL_CALL
     * - TOOL_RESULT
     * - FINISH
     * - ERROR
     * - CANCELLED
     */
    @GetMapping("/{taskId}/stream")
    public SseEmitter stream(@PathVariable String taskId) {
        return agentSseService.connect(taskId);
    }

    @GetMapping
    public Result<List<TaskDetailVO>> getTasks() {
        List<TaskDetailVO> tasks = agentTaskService.getTasks();
        return Result.success(tasks);
    }

    @GetMapping("/{taskId}/events")
    public Result<List<AgentEventVO>> getEvents(@PathVariable String taskId, @RequestParam(required = false) String runId) {
        List<AgentEventVO> taskEvents = agentTaskService.getTaskEvents(taskId, runId);
        return Result.success(taskEvents);
    }

    @GetMapping("/{taskId}/runs")
    public Result<List<AgentRunVO>> getRuns(@PathVariable String taskId) {
        List<AgentRunVO> runs = agentRunService.getRuns(taskId);
        return Result.success(runs);
    }

    @GetMapping("/{taskId}/result")
    public Result<TaskResultVO> getResult(@PathVariable String taskId, @RequestParam(required = false) String runId) {
        TaskResultVO taskResult = agentTaskService.getTaskResult(taskId, runId);
        return Result.success(taskResult);
    }

    @PostMapping("/{taskId}/resume")
    public Result<TaskOperateVO> resume(@PathVariable String taskId, @RequestParam String runId) {
        TaskOperateVO taskOperateVO = agentTaskService.resumeTask(taskId, runId);
        return Result.success(taskOperateVO);
    }

    @PostMapping("/{taskId}/retry")
    public Result<TaskOperateVO> retry(@PathVariable String taskId) {
        TaskOperateVO taskOperateVO = agentTaskService.retryTask(taskId);
        return Result.success(taskOperateVO);
    }

    @PostMapping("/{taskId}/cancel")
    public Result<TaskOperateVO> cancel(@PathVariable String taskId, @RequestParam String runId) {
        TaskOperateVO taskOperateVO = agentTaskService.cancelTask(taskId, runId);
        return Result.success(taskOperateVO);
    }
}