package com.xd.runtime.state;

import com.xd.model.enums.AgentActionCommandEnum;
import com.xd.model.enums.AgentRunCommandEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.AgentTaskStatusEnum;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

import static com.xd.model.enums.AgentRunCommandEnum.*;
import static com.xd.model.enums.AgentEventEnum.*;
import static com.xd.model.enums.AgentTaskStatusEnum.*;

@Component
public class AgentTaskStateMachine {

    /**
     * Agent Runtime 内部 Event：
     * <p>
     * 当前 State + AgentEvent
     * ↓
     * Next State
     */
    private final Map<AgentTaskStatusEnum, Map<AgentEventEnum, AgentStateTransition>> eventTransitions =
            new EnumMap<>(AgentTaskStatusEnum.class);

    /**
     * 外部 Command：
     * <p>
     * 当前 State + AgentCommand
     */
    private final Map<AgentTaskStatusEnum, Map<AgentRunCommandEnum, AgentStateTransition>> commandTransitions =
            new EnumMap<>(AgentTaskStatusEnum.class);

    private final Map<AgentTaskStatusEnum, Map<AgentActionCommandEnum, AgentStateTransition>> actionCommandTransitions =
            new EnumMap<>(AgentTaskStatusEnum.class);

    public AgentTaskStateMachine() {
        registerEventTransitions();
        registerCommandTransitions();
        registerActionCommandTransitions();
    }

    /**
     * =========================================================
     * Agent Event Transition
     * =========================================================
     */
    private void registerEventTransitions() {

        /**
         * 没有排队机制前先加入这个
         */
        registerEvent(AgentTaskStatusEnum.CREATED, AgentEventEnum.THINK, AgentTaskStatusEnum.AGENT_THINKING);

        registerEvent(AGENT_THINKING, TOOL_WAITING, WAITING_HUMAN);
        registerEvent(TOOL_CALLING, TOOL_CALL, TOOL_CALLING);
        registerEvent(AGENT_THINKING, TOOL_RESULT, AGENT_THINKING);
        /*
         * THINKING
         *
         * Agent 连续产生 THINK，
         * 状态保持 THINKING。
         */
        registerEvent(AGENT_THINKING, THINK, AGENT_THINKING);

        /*
         * Agent 开始调用工具：
         *
         * THINKING -> EXECUTING
         */
        registerEvent(AGENT_THINKING, TOOL_CALL, TOOL_CALLING);

        /*
         * 工具返回：
         *
         * EXECUTING -> THINKING
         */
        registerEvent(TOOL_CALLING, TOOL_RESULT, AGENT_THINKING);

        /*
         * 正常结束：
         *
         * THINKING -> FINISHED
         */
        registerEvent(AGENT_THINKING, FINISH, COMPLETED);

        /*
         * Agent 推理阶段失败：
         *
         * THINKING -> ERROR
         */
        registerEvent(AGENT_THINKING, ERROR, NEED_RETRY);

        /*
         * 工具执行阶段发生错误：
         *
         * EXECUTING -> ERROR
         */
        registerEvent(TOOL_CALLING, ERROR, NEED_RETRY);
    }

    /**
     * =========================================================
     * Command Transition
     * =========================================================
     */
    private void registerCommandTransitions() {

        /*
         * START：
         *
         * QUEUED -> THINKING
         *
         * 注意：
         * CREATED -> QUEUED 不属于 AgentCommand，
         * 它属于创建 / 调度流程。
         */
        registerCommand(QUEUED, START, AGENT_THINKING);

        /*
         * RESUME：
         *
         * WAITING_HUMAN -> THINKING
         */
        registerCommand(WAITING_HUMAN, RESUME, AGENT_THINKING);

        /*
         * RESUME 也允许从 CANCELLED 恢复 checkpoint。
         */
        registerCommand(CANCELLED, RESUME, AGENT_THINKING);

        /*
         * CANCEL：
         *
         * CREATED -> CANCELLED
         */
        registerCommand(CREATED, CANCEL, CANCELLED);

        /*
         * QUEUED -> CANCELLED
         */
        registerCommand(QUEUED, CANCEL, CANCELLED);

        /*
         * THINKING -> CANCELLED
         */
        registerCommand(AGENT_THINKING, CANCEL, CANCELLED);

        /*
         * EXECUTING -> CANCELLED
         */
        registerCommand(TOOL_CALLING, CANCEL, CANCELLED);

        /*
         * WAITING_HUMAN -> CANCELLED
         */
        registerCommand(WAITING_HUMAN, CANCEL, CANCELLED);

        /*
         * 注意：
         *
         * RETRY 不注册。
         *
         * ERROR -> RETRY 并不是简单的状态迁移，
         * 而是：
         *
         *   Task
         *     ↓
         *   创建新的 Run
         *     ↓
         *   New Run = QUEUED
         *
         * 所以它应该由 AgentTaskService / RunService
         * 负责，而不是 StateMachine。
         */
    }

    /**
     * =========================================================
     * Action Command Transition
     * =========================================================
     */
    private void registerActionCommandTransitions() {

        /*
         * 用户批准：
         *
         * WAITING_HUMAN -> TOOL_CALLING
         *
         * Java 告诉 Python：
         * 可以继续执行当前 Tool。
         */
        registerActionCommand(WAITING_HUMAN, AgentActionCommandEnum.APPROVE, TOOL_CALLING);

        /*
         * 用户拒绝：
         *
         * WAITING_HUMAN -> AGENT_THINKING
         *
         * Python 收到 REJECT 后：
         * 将 USER_REJECTED 作为 Tool Result，
         * 下一轮重新 THINK。
         */
        registerActionCommand(WAITING_HUMAN, AgentActionCommandEnum.REJECT, AGENT_THINKING);
    }

    /**
     * =========================================================
     * Event
     * =========================================================
     */
    public AgentTaskStatusEnum transition(AgentTaskStatusEnum currentState, AgentEventEnum event) {

        if (currentState == null) {
            throw new IllegalArgumentException("当前 Agent 状态不能为空");
        }

        if (event == null) {
            throw new IllegalArgumentException("Agent Event 不能为空");
        }

        Map<AgentEventEnum, AgentStateTransition> transitions = eventTransitions.get(currentState);

        if (transitions == null) {
            throw illegalTransition(currentState, event);
        }

        AgentStateTransition transition = transitions.get(event);

        if (transition == null) {
            throw illegalTransition(currentState, event);
        }

        return transition.getTo();
    }

    /**
     * =========================================================
     * Command
     * =========================================================
     */
    public AgentTaskStatusEnum transition(AgentTaskStatusEnum currentState, AgentRunCommandEnum command) {

        if (currentState == null) {
            throw new IllegalArgumentException("当前 Agent 状态不能为空");
        }

        if (command == null) {
            throw new IllegalArgumentException("Agent Command 不能为空");
        }

        Map<AgentRunCommandEnum, AgentStateTransition> transitions = commandTransitions.get(currentState);

        if (transitions == null) {
            throw illegalTransition(currentState, command);
        }

        AgentStateTransition transition = transitions.get(command);

        if (transition == null) {
            throw illegalTransition(currentState, command);
        }

        return transition.getTo();
    }

    public AgentTaskStatusEnum transition(AgentTaskStatusEnum currentState, AgentActionCommandEnum command) {

        if (currentState == null) {
            throw new IllegalArgumentException("当前 Agent 状态不能为空");
        }

        if (command == null) {
            throw new IllegalArgumentException("Agent Action Command 不能为空");
        }

        Map<AgentActionCommandEnum, AgentStateTransition> transitions =
                actionCommandTransitions.get(currentState);

        if (transitions == null) {
            throw illegalTransition(currentState, command);
        }

        AgentStateTransition transition = transitions.get(command);

        if (transition == null) {
            throw illegalTransition(currentState, command);
        }

        return transition.getTo();
    }

    /**
     * =========================================================
     * Can Transition
     * =========================================================
     */
    public boolean canTransition(AgentTaskStatusEnum currentState, AgentEventEnum event) {
        if (currentState == null || event == null) {
            return false;
        }
        Map<AgentEventEnum, AgentStateTransition> transitions = eventTransitions.get(currentState);
        return transitions != null && transitions.containsKey(event);
    }

    public boolean canTransition(AgentTaskStatusEnum currentState, AgentRunCommandEnum command) {
        if (currentState == null || command == null) {
            return false;
        }
        Map<AgentRunCommandEnum, AgentStateTransition> transitions = commandTransitions.get(currentState);
        return transitions != null && transitions.containsKey(command);
    }

    /**
     * =========================================================
     * Register
     * =========================================================
     */
    private void registerEvent(AgentTaskStatusEnum from, AgentEventEnum event, AgentTaskStatusEnum to) {
        eventTransitions.computeIfAbsent(from, key -> new EnumMap<>(AgentEventEnum.class))
                .put(event, AgentStateTransition.event(from, event, to));
    }

    private void registerCommand(AgentTaskStatusEnum from, AgentRunCommandEnum command, AgentTaskStatusEnum to) {
        commandTransitions.computeIfAbsent(from, key -> new EnumMap<>(AgentRunCommandEnum.class))
                .put(command, AgentStateTransition.command(from, command, to));
    }

    private void registerActionCommand(AgentTaskStatusEnum from, AgentActionCommandEnum command, AgentTaskStatusEnum to) {
        actionCommandTransitions.computeIfAbsent(from, key -> new EnumMap<>(AgentActionCommandEnum.class))
                .put(command, AgentStateTransition.actionCommand(from, command, to));
    }

    /**
     * =========================================================
     * Error
     * =========================================================
     */
    private IllegalStateException illegalTransition(AgentTaskStatusEnum state, Object input) {
        return new IllegalStateException("非法 Agent Run 状态迁移: " + "state=" + state + ", input=" + input);
    }

}