package com.xd.mapper;

import com.xd.model.entity.WorkspaceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkSpaceMapper {

    int insertWorkspace(WorkspaceDO workspace);

    WorkspaceDO selectByWorkspaceId(
            String workspaceId
    );

    WorkspaceDO selectBySessionId(
            String sessionId
    );

    int updateWorkspace(
            WorkspaceDO workspace
    );
}
