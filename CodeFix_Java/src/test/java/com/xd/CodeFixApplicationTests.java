package com.xd;


import com.xd.model.enums.AgentRunCommandEnum;
import com.xd.model.enums.AgentEventEnum;
import com.xd.model.enums.AgentTaskStatusEnum;
import com.xd.state.AgentTaskStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class test {
    private final AgentTaskStateMachine stateMachine = new AgentTaskStateMachine();

    @Test
    void thinkingToolCallToExecuting() {
        assertEquals(
                AgentTaskStatusEnum.TOOL_CALLING,
                stateMachine.transition(
                        AgentTaskStatusEnum.AGENT_THINKING,
                        AgentEventEnum.TOOL_CALL
                )
        );
    }

    @Test
    void executingToolResultToThinking() {
        assertEquals(
                AgentTaskStatusEnum.AGENT_THINKING,
                stateMachine.transition(
                        AgentTaskStatusEnum.TOOL_CALLING,
                        AgentEventEnum.TOOL_RESULT
                )
        );
    }

    @Test
    void thinkingFinishToCompleted() {
        assertEquals(
                AgentTaskStatusEnum.COMPLETED,
                stateMachine.transition(
                        AgentTaskStatusEnum.AGENT_THINKING,
                        AgentEventEnum.FINISH
                )
        );
    }

    @Test
    void thinkingCancelToCancelled() {
        assertEquals(
                AgentTaskStatusEnum.CANCELLED,
                stateMachine.transition(
                        AgentTaskStatusEnum.AGENT_THINKING,
                        AgentRunCommandEnum.CANCEL
                )
        );
    }

    @Test
    void finishedCannotToolCall() {
        assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(
                        AgentTaskStatusEnum.COMPLETED,
                        AgentEventEnum.TOOL_CALL
                )
        );
    }

    @Test
    void cancelledCannotResumeWithoutTransition() {
        assertTrue(
                stateMachine.canTransition(
                        AgentTaskStatusEnum.CANCELLED,
                        AgentRunCommandEnum.RESUME
                )
        );
    }
}