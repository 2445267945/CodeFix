package com.xd.service.impl;

import com.xd.assembler.AgentFileChangeAssembler;
import com.xd.mapper.AgentFileChangeMapper;
import com.xd.model.dto.AgentMessageDTO;
import com.xd.model.entity.AgentEventDO;
import com.xd.model.entity.AgentFileChangeDO;
import com.xd.model.vo.FileChangeVO;
import com.xd.model.vo.FileDiffVO;
import com.xd.service.AgentFileChangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentFileChangeServiceImpl implements AgentFileChangeService {

    @Autowired
    private AgentFileChangeMapper agentFileChangeMapper;
    @Autowired
    private AgentFileChangeAssembler agentFileChangeAssembler;

    @Override
    public String save(AgentMessageDTO message, AgentEventDO event, FileChangeVO fileChange) {
        try {
            String diffId = UUID.randomUUID().toString();
            AgentFileChangeDO change = new AgentFileChangeDO();
            change.setDiffId(diffId);
            change.setSessionId(message.getSessionId());
            change.setTaskId(message.getTaskId());
            change.setRunId(message.getRunId());
            change.setEventId(event.getId());
            change.setFilePath(fileChange.getFilePath());
            change.setOperation(fileChange.getOperation());
            change.setAddedLines(fileChange.getAddedLines());
            change.setRemovedLines(fileChange.getRemovedLines());
            change.setDiffText(fileChange.getDiff());
            change.setCreatedAt(System.currentTimeMillis());
            agentFileChangeMapper.insert(change);
            return diffId;
        } catch (Exception e) {
            log.info("保存文件{}失败", fileChange.getFilePath(), e);
        }
        return null;
    }

    @Override
    public FileDiffVO getByDiffId(String diffId) {
        AgentFileChangeDO changeDO = agentFileChangeMapper.selectByDiffId(diffId);
        if (changeDO == null) {
            throw new RuntimeException("文件变更不存在: " + diffId);
        }

        return FileDiffVO.builder()
                .diffId(changeDO.getDiffId())
                .taskId(changeDO.getTaskId())
                .runId(changeDO.getRunId())
                .filePath(changeDO.getFilePath())
                .operation(changeDO.getOperation())
                .addedLines(changeDO.getAddedLines())
                .removedLines(changeDO.getRemovedLines())
                .diff(changeDO.getDiffText())
                .createdAt(changeDO.getCreatedAt())
                .build();
    }

    @Override
    public FileChangeVO parse(AgentMessageDTO messageDTO) {
        return agentFileChangeAssembler.parse(messageDTO);
    }

    @Override
    public List<AgentFileChangeDO> getByTaskIds(List<String> taskIds) {
        return agentFileChangeMapper.selectByTaskIds(taskIds);
    }
}