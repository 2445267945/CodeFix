package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentRunDO;
import com.xd.model.vo.AgentRunVO;

import java.util.List;

public interface AgentRunService {

    /**
     * 创建一个新的 Run
     *
     * @param taskId    所属 Task
     * @param sessionId 所属 Session
     * @param status    初始状态
     * @return 创建后的 Run
     */
    AgentRunDO createRun(
            String runId,
            String taskId,
            String sessionId,
            Integer status
    );

    /**
     * 更新 Run
     *
     * 目前主要根据 AgentMessage 更新状态，
     * 后续可以继续扩展 startedAt / endedAt / errorMessage 等字段。
     */
    void updateRun(AgentMessageDTO messageDTO);

    /**
     * 查询一个 Task 的所有 Run
     */
    List<AgentRunVO> getRuns(String taskId);


    /**
     * 查询一个 Task 下指定的 Run
     */
    AgentRunDO getRun(String taskId, String runId);
}
