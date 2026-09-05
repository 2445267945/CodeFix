package com.xd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.xd.mapper.AgentTaskMapper;
import com.xd.model.dto.AgentHeartbeatDTO;
import com.xd.model.entity.AgentTaskDO;
import com.xd.mq.MessageHandler;
import com.xd.service.AgentHeartbeatHandlerService;
import com.xd.service.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("AGENT_HEARTBEAT")
public class AgentHeartbeatHandlerServiceImpl implements AgentHeartbeatHandlerService, MessageHandler {

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Override
    public void handleMsg(String messageJson) {
        AgentHeartbeatDTO heartbeat;
        try {
            heartbeat = JSON.parseObject(messageJson, AgentHeartbeatDTO.class);
        } catch (Exception e) {
            log.error("Agent heartbeat JSON解析失败: {}", messageJson, e);
            throw e;
        }
        boolean updated = updateHeartbeat(heartbeat);
        if (!updated) {
            log.debug("忽略无效 heartbeat: taskId={}, runId={}", heartbeat.getTaskId(), heartbeat.getRunId());
        }
    }

    @Override
    public boolean updateHeartbeat(AgentHeartbeatDTO heartbeat) {
        AgentTaskDO updateTask = new AgentTaskDO();
        updateTask.setTaskId(heartbeat.getTaskId());
        updateTask.setRunId(heartbeat.getRunId());
        updateTask.setLastHeartbeatAt(heartbeat.getTimestamp());
        return agentTaskMapper.updateHeartbeat(updateTask) > 0;
    }
}
