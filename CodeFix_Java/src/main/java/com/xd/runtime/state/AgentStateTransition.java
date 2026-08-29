package com.xd.runtime.state;

import com.xd.model.enums.AgentActionCommandEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.AgentRunCommandEnum;
import com.xd.model.enums.AgentTaskStatusEnum;
import lombok.Getter;

/**
 * Agent Runtime 状态迁移定义。
 * <p>
 * 输入来源有三种：
 * <p>
 * 1. AgentEventEnum
 * Agent Runtime 内部事件
 * <p>
 * 2. AgentRunCommandEnum
 * 用户 / 系统 Run 生命周期控制命令
 * <p>
 * 3. AgentActionCommandEnum
 * 用户对当前 Action 的控制命令
 * <p>
 * 本质：
 * <p>
 * currentState + input -> nextState
 */
@Getter
public class AgentStateTransition {

    private final AgentTaskStatusEnum from;

    private final AgentEventEnum event;

    private final AgentRunCommandEnum command;

    private final AgentActionCommandEnum actionCommand;

    private final AgentTaskStatusEnum to;

    private AgentStateTransition(AgentTaskStatusEnum from, AgentEventEnum event, AgentRunCommandEnum command
            , AgentActionCommandEnum actionCommand, AgentTaskStatusEnum to) {
        this.from = from;
        this.event = event;
        this.command = command;
        this.actionCommand = actionCommand;
        this.to = to;
    }

    /**
     * Agent Event 驱动。
     */
    public static AgentStateTransition event(AgentTaskStatusEnum from, AgentEventEnum event, AgentTaskStatusEnum to) {
        return new AgentStateTransition(from, event, null, null, to);
    }

    /**
     * Run Command 驱动。
     */
    public static AgentStateTransition command(AgentTaskStatusEnum from, AgentRunCommandEnum command, AgentTaskStatusEnum to) {
        return new AgentStateTransition(from, null, command, null, to);
    }

    /**
     * Action Command 驱动。
     */
    public static AgentStateTransition actionCommand(AgentTaskStatusEnum from, AgentActionCommandEnum actionCommand, AgentTaskStatusEnum to) {
        return new AgentStateTransition(from, null, null, actionCommand, to);
    }

    public boolean isEventTransition() {
        return event != null;
    }

    public boolean isCommandTransition() {
        return command != null;
    }

    public boolean isActionCommandTransition() {
        return actionCommand != null;
    }
}