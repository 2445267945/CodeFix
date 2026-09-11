package com.xd.mapper;

import com.xd.model.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insertChatMessage(ChatMessageDO message);

    List<ChatMessageDO> selectBySessionId(String sessionId);

    List<ChatMessageDO> selectByTaskIdAndRunId(String taskId, String runId);

    List<ChatMessageDO> selectByTaskIds(List<String> taskId);

    List<ChatMessageDO> selectRecentBySessionId(@Param("list") List<String> sessionIds, @Param("round") Integer round);

}