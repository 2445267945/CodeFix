package com.xd.mapper;

import com.xd.model.entity.WorkspaceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkSpaceMapper {

    int insertWorkspace(WorkspaceDO workspace);

    WorkspaceDO selectByWorkspaceId(String workspaceId);

    WorkspaceDO selectBySessionId(String sessionId);

    int updateWorkspace(WorkspaceDO workspace);

    List<WorkspaceDO> selectAllWorkspaces();
}
