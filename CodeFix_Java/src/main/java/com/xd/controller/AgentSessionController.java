package com.xd.controller;

import com.xd.model.Result;
import com.xd.model.dto.ChatMessageCreateDTO;
import com.xd.model.entity.AgentSessionDO;
import com.xd.model.vo.*;
import com.xd.service.AgentChatAssemblerService;
import com.xd.service.AgentConversationService;
import com.xd.service.AgentSessionService;
import com.xd.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent/sessions")
public class AgentSessionController {

    @Autowired
    private AgentSessionService agentSessionService;
    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private AgentConversationService agentConversationService;
    @Autowired
    private AgentChatAssemblerService agentChatAssemblerService;


    /**
     * 查询 Session
     */
    @GetMapping("/{sessionId}")
    public Result<AgentSessionVO> getSession(@PathVariable String sessionId) {
        AgentSessionVO session = agentSessionService.getSessionById(sessionId);
        return Result.success(session);
    }

    /**
     * 查询 Session 下的所有 Task
     */
    @GetMapping("/{sessionId}/tasks")
    public Result<List<TaskDetailVO>> getSessionTasks(@PathVariable String sessionId) {
        // 先确认 Session 存在
        agentSessionService.getSessionById(sessionId);
        List<TaskDetailVO> tasksBySessionId = agentTaskService.getTasksBySessionId(sessionId);
        return Result.success(tasksBySessionId);
    }

    /**
     * 查询 Session 聊天记录
     */
    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMessageVO>> getMessages(@PathVariable String sessionId) {
        List<ChatMessageVO> messages = agentConversationService.getMessages(sessionId);
        return Result.success(messages);
    }

    /**
     * 在 Session 中发送一条新消息

     * 一条消息会创建一个新的 Task + session + Run
     */
    @PostMapping("/messages")
    public Result<ChatMessageVO> sendMessage(@Validated @RequestBody ChatMessageCreateDTO request) {
        ChatMessageVO chatMessageVO = agentConversationService.sendMessage(request);
        return Result.success(chatMessageVO);
    }

    @GetMapping
    public Result<List<SessionVO>> getMessages() {
        List<SessionVO> messages = agentConversationService.getMessages();
        return Result.success(messages);
    }

    @GetMapping("/{sessionId}/chat")
    public Result<AgentChatViewVO> getChat(@PathVariable String sessionId) {
        AgentChatViewVO assemble = agentChatAssemblerService.assemble(sessionId);
        return Result.success(assemble);
    }
}