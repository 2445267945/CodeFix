package com.xd.mapper;

import com.xd.model.entity.AgentRunStateHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentRunStateHistoryMapper {

    /**
     * 插入一次 Run 状态迁移历史。
     */
    int insertStateHistory(AgentRunStateHistoryDO history);

    /**
     * 查询某次 Run 的最新状态历史。
     */
    AgentRunStateHistoryDO selectLatestByRunId(@Param("runId") String runId);
}