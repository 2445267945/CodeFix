package com.xd.context;

import com.xd.model.entity.AgentFileChangeDO;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * Agent Chat 历史组装上下文。
 
 * 用于在 Session -> Task -> Event -> Block
 * 的组装过程中传递已经批量查询好的关联数据。
 */
@Data
@Builder
public class AgentChatAssembleContext {

    /**
     * Event ID -> FileChange
     
     * 用于历史 Chat Block 回填 diffId。
     */
    @Builder.Default
    private Map<Long, AgentFileChangeDO> fileChanges = Collections.emptyMap();
}