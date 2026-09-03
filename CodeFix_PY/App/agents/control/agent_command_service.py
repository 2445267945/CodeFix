import logging

from App.infrastructure.message.base_message import BaseMessage
from App.models.agent_command_message import AgentCommandMessage
from App.models.enum.agent_command import (AgentCommandType, AgentActionCommand)
from App.infrastructure.handler.base_handler import BaseMsgHandler
from App.agents.control.execution_gate import ExecutionDecision
from App.agents.manager.agent_run_manager import AgentRunManager


logger = logging.getLogger(__name__)


class AgentCommandService(BaseMsgHandler):

    def __init__(self, run_manager: AgentRunManager):
        self.run_manager = run_manager

    def handle(self, message: BaseMessage) -> None:
        msg: AgentCommandMessage = message

        if msg.command_type is AgentCommandType.ACTION:
            self.handle_action_command(msg)
            return

        logger.warning("暂不支持的 Agent Command Type: %s", msg.command_type)

    def handle_action_command(self,msg: AgentCommandMessage) -> None:
        if not msg.run_id:
            logger.warning("ACTION command 缺少 runId")
            return
        if not msg.action_id:
            logger.warning("ACTION command 缺少 actionId: runId=%s", msg.run_id)
            return
        # 通过 runId 找到真正运行中的 Run
        run = self.run_manager.get(msg.run_id)

        if run is None:
            logger.warning("Run 不存在或已经结束: runId=%s", msg.run_id)
            return
        if msg.command == AgentActionCommand.APPROVE.value:
            decision = ExecutionDecision.ALLOW
        elif msg.command == AgentActionCommand.REJECT.value:
            decision = ExecutionDecision.DENY
        else:
            logger.warning(
                "未知 Agent Action Command: "
                "runId=%s, actionId=%s, command=%s",
                msg.run_id,
                msg.action_id,
                msg.command
            )
            return

        # ExecutionGate 属于这个 Run 的 RunContext
        execution_gate = run.agent.run_context.execution_gate

        handled = execution_gate.resolve(action_id=msg.action_id, decision=decision)

        logger.info(
            "Agent Action Command handled: "
            "runId=%s, actionId=%s, command=%s, handled=%s",
            msg.run_id,
            msg.action_id,
            msg.command,
            handled
        )