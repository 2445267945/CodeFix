package com.xd.controller;

import com.xd.controller.request.AgentCommandRequest;
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
@RequestMapping("/api/audit/tasks")
public class AuditTaskController {

    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentSseService agentSseService;
    @Autowired
    private AgentRunService agentRunService;


//    @PostMapping
//    public TaskCreateVO createTask(@Validated @RequestBody AuditTaskCreateDTO request) {
//        return agentTaskService.createTask(request);
//    }

    @PostMapping("/{taskId}/command")
    public AgentStateTransitionResult handleCommand(@PathVariable String taskId, @RequestBody AgentCommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Command 请求不能为空");
        }
        return agentTaskService.handleUserCommand(taskId, request.getRunId(), request.getActionId(), request.getCommand());
    }

    @GetMapping("/{taskId}")
    public TaskDetailVO getTask(@PathVariable String taskId) {
        return agentTaskService.getTask(taskId);
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
    public List<TaskDetailVO> getTasks() {
        return agentTaskService.getTasks();
    }

    @GetMapping("/{taskId}/events")
    public List<AgentEventVO> getEvents(@PathVariable String taskId, @RequestParam(required = false) String runId) {
        return agentTaskService.getTaskEvents(taskId, runId);
    }

    @GetMapping("/{taskId}/runs")
    public List<AgentRunVO> getRuns(@PathVariable String taskId) {
        return agentRunService.getRuns(taskId);
    }

    @GetMapping("/{taskId}/result")
    public TaskResultVO getResult(@PathVariable String taskId, @RequestParam(required = false) String runId) {
        return agentTaskService.getTaskResult(taskId, runId);
    }

    @PostMapping("/{taskId}/resume")
    public TaskOperateVO resume(@PathVariable String taskId, @RequestParam String runId) {
        return agentTaskService.resumeTask(taskId, runId);
    }

    @PostMapping("/{taskId}/retry")
    public TaskOperateVO retry(@PathVariable String taskId) {
        return agentTaskService.retryTask(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskOperateVO cancel(@PathVariable String taskId, @RequestParam String runId) {
        return agentTaskService.cancelTask(taskId, runId);
    }
}