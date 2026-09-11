package com.xd.mapper;

import com.xd.model.entity.WorkspaceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkSpaceMapper {

    int insertWorkspace(WorkspaceDO workspace);

    WorkspaceDO selectByWorkspaceId(String workspaceId);

    int updateWorkspace(WorkspaceDO workspace);

    List<WorkspaceDO> selectAllWorkspaces();

    WorkspaceDO selectWorkspaceByRootPath(@Param("rootPath") String rootPath);
}
