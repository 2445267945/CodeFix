package com.xd.controller;

import com.xd.model.dto.AuditTaskCreateDTO;
import com.xd.model.vo.*;
import com.xd.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit/tasks")
@RequiredArgsConstructor
public class AuditTaskController {

    private final AgentTaskService agentTaskService;

    @PostMapping
    public TaskCreateVO createTask(@Validated @RequestBody AuditTaskCreateDTO request) {
        return agentTaskService.createTask(request);
    }

    @GetMapping("/{taskId}")
    public TaskDetailVO getTask(@PathVariable String taskId) {
        return agentTaskService.getTask(taskId);
    }

    @GetMapping("/{taskId}/events")
    public List<AgentEventVO> getEvents(@PathVariable String taskId,  @RequestParam(required = false) String runId) {
        return agentTaskService.getTaskEvents(taskId, runId);
    }

    @GetMapping("/{taskId}/result")
    public TaskResultVO getResult(@PathVariable String taskId) {
        return agentTaskService.getTaskResult(taskId);
    }

    @PostMapping("/{taskId}/resume")
    public TaskOperateVO resume(@PathVariable String taskId,  @RequestParam String runId) {
        return agentTaskService.resumeTask(taskId, runId);
    }

    @PostMapping("/{taskId}/retry")
    public TaskOperateVO retry(@PathVariable String taskId) {
        return agentTaskService.retryTask(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskOperateVO cancel(@PathVariable String taskId,  @RequestParam String runId) {
        return agentTaskService.cancelTask(taskId, runId);
    }
}