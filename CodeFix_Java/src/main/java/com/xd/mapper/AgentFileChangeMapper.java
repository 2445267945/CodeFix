package com.xd.mapper;


import com.xd.model.entity.AgentFileChangeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentFileChangeMapper {

    int insert(AgentFileChangeDO change);

    AgentFileChangeDO selectByDiffId(String diffId);

    List<AgentFileChangeDO> selectByTaskIds(List<String> taskIds);
}