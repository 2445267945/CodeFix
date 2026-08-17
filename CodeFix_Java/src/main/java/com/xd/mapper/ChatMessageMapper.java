package com.xd.mapper;

import com.xd.model.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insertChatMessage(ChatMessageDO message);

    List<ChatMessageDO> selectBySessionId(String sessionId);

    List<ChatMessageDO> selectByTaskIdAndRunId(String taskId, String runId);
}