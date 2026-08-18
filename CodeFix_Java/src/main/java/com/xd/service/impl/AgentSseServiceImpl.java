package com.xd.service.impl;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.vo.AgentChatStreamVO;
import com.xd.service.AgentSseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentSseServiceImpl implements AgentSseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(String taskId) {
        // 第一版先设置30分钟超时
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        log.info("建立SSE连接: taskId={}", taskId);
        // 关键：保存连接
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> {
            emitters.remove(taskId, emitter);
            log.info("SSE连接完成: taskId={}", taskId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(taskId, emitter);
            log.info("SSE连接超时: taskId={}", taskId);
            emitter.complete();
        });
        emitter.onError(error -> {
            emitters.remove(taskId, emitter);
            log.warn("SSE连接异常: taskId={}", taskId, error);
        });
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("SSE连接成功"));
        } catch (Exception e) {
            emitters.remove(taskId, emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @Override
    public void send(AgentChatStreamVO message) {

        String taskId = message.getTaskId();

        if (taskId == null || taskId.isBlank()) {
            log.warn("SSE推送失败：taskId为空");
            return;
        }

        SseEmitter emitter = emitters.get(taskId);

        if (emitter == null) {
            log.debug("当前Task没有SSE连接: taskId={}, type={}", taskId, message.getType());
            return;
        }

        try {
            emitter.send(SseEmitter.event().name(message.getType()).id(message.getMessageId()).data(message));

        } catch (IOException e) {
            emitters.remove(taskId, emitter);
            emitter.completeWithError(e);
        }
    }


}