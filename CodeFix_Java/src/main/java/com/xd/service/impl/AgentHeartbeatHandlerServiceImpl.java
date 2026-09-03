package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xd.model.dto.AgentHeartbeatDTO;
import com.xd.mq.MessageHandler;
import com.xd.service.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("AGENT_HEARTBEAT")
public class AgentHeartbeatHandlerServiceImpl implements MessageHandler {

    @Autowired
    private AgentTaskService agentTaskService;

    @Override
    public void handleMsg(String messageJson) {
        AgentHeartbeatDTO heartbeat;
        try {
            heartbeat = JSON.parseObject(messageJson, AgentHeartbeatDTO.class);
        } catch (Exception e) {
            log.error("Agent heartbeat JSON解析失败: {}", messageJson, e);
            throw e;
        }
        boolean updated = agentTaskService.updateHeartbeat(heartbeat);
        if (!updated) {
            log.debug("忽略无效 heartbeat: taskId={}, runId={}", heartbeat.getTaskId(), heartbeat.getRunId());
        }
    }
}
