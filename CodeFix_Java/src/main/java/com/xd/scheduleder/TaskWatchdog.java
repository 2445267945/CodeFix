package com.xd.scheduleder;

import com.xd.mapper.AgentTaskMapper;
import com.xd.model.entity.AgentTaskDO;
import com.xd.service.AgentTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class TaskWatchdog {

    /**
     * Heartbeat 间隔：5 秒
     * 允许一定的网络 / MQ / 调度抖动
     */
    private static final long HEARTBEAT_TIMEOUT_MS = 15_000L;

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Autowired
    private AgentTaskService agentTaskService;

    /**
     * 每 5 秒检查一次正在执行中的 Task。
     */
    @Scheduled(fixedDelay = 5_000L)
    public void taskScan() {
        long now = System.currentTimeMillis();
        List<AgentTaskDO> tasks = agentTaskMapper.selectHeartbeatTasks();
        for (AgentTaskDO task : tasks) {
            Long lastHeartbeatAt = task.getLastHeartbeatAt();
            if (lastHeartbeatAt == null) {
                continue;
            }
            if (now - lastHeartbeatAt <= HEARTBEAT_TIMEOUT_MS) {
                continue;
            }
            agentTaskService.handleHeartbeatTimeout(task.getTaskId(), task.getRunId());
        }
    }
}
