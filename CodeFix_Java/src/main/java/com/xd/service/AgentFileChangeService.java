package com.xd.service;

import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.FileChangeVO;
import com.xd.model.vo.FileDiffVO;

import java.util.List;

public interface AgentFileChangeService {

    String save(AgentMessageDTO message, AgentEventDO event, FileChangeVO fileChange);

    FileDiffVO getByDiffId(String diffId);

    FileChangeVO parse(AgentMessageDTO messageDTO);

    List<AgentFileChangeDO> getByTaskIds(List<String> taskIds);
}