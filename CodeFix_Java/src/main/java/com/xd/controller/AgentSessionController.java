package com.xd.controller;

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
@RequestMapping("/api/audit/sessions")
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
    public AgentSessionVO getSession(@PathVariable String sessionId) {
        AgentSessionDO session = agentSessionService.getSessionById(sessionId);
        return AgentSessionVO.builder()
                .sessionId(session.getSessionId())
                .workspaceId(session.getWorkspaceId())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    /**
     * 查询 Session 下的所有 Task
     */
    @GetMapping("/{sessionId}/tasks")
    public List<TaskDetailVO> getSessionTasks(@PathVariable String sessionId) {
        // 先确认 Session 存在
        agentSessionService.getSessionById(sessionId);
        return agentTaskService.getTasksBySessionId(sessionId);
    }

    /**
     * 查询 Session 聊天记录
     */
    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageVO> getMessages(@PathVariable String sessionId) {
        return agentConversationService.getMessages(sessionId);
    }

    /**
     * 在 Session 中发送一条新消息
     *
     * 一条消息会创建一个新的 Task + Run
     */
    @PostMapping("/messages")
    public ChatMessageVO sendMessage(@Validated @RequestBody ChatMessageCreateDTO request) {
        return agentConversationService.sendMessage(request);
    }

    @GetMapping
    public List<SessionVO> getMessages(){
        return agentConversationService.getMessages();
    }

    @GetMapping("/{sessionId}/chat")
    public AgentChatViewVO getChat(@PathVariable String sessionId) {
        return agentChatAssemblerService.assemble(sessionId);
    }
}