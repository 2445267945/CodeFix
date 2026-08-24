import logging
import asyncio
import threading
import time
from uuid import uuid4

from App.agents.base_agent import BaseAgent
from App.agents.context.agent_context import AgentContext
from App.infrastructure.message.base_message import BaseMessage
from App.infrastructure.mq.event_bus import event_bus
from App.models.agent_message import AgentMessage
from App.services.base_handler import BaseMsgHandler
from App.services.llm_factory import llm_factory
from .redis_working_memory_store import RedisWorkingMemoryStore
from ...agents.control.agent_command_service import AgentCommandService
from ...agents.manager.agent_run_manager import AgentRunManager
from ...infrastructure.redis.redis_client import redis_service
from App.models.enum.agent_event import AgentEvent
from ...models.enum.agent_command import AgentRunCommand

logger = logging.getLogger(__name__)
main_llm = llm_factory.get_llm(4096, 0.1, "mid")
compress_llm = llm_factory.get_llm(4096, 0.1, "low")


class AgentMsgService(BaseMsgHandler):
    """处理 AGENT_MSG 类型的消息"""

    def __init__(self):
        self.command_handlers = {
            AgentRunCommand.START.value: self.handle_start,
            AgentRunCommand.RESUME.value: self.handle_resume,
            AgentRunCommand.RETRY.value: self.handle_retry,
            AgentRunCommand.CANCEL.value: self.handle_cancel
        }
        working_memory_store = RedisWorkingMemoryStore(redis_service)
        self.context = AgentContext(
            main_llm=main_llm, compress_llm=compress_llm,
            msg_sender=self,
            working_memory_store=working_memory_store,
        )
        self.loop = asyncio.new_event_loop()
        self.run_manager = AgentRunManager(
            loop=self.loop,
            context=self.context
        )
        self.command_service = AgentCommandService(
            run_manager=self.run_manager
        )
        self.thread = threading.Thread(
            target=self.run_loop,
            daemon=True
        )
        self.thread.start()

    def handle(self, message: BaseMessage) -> None:
        """统一入口方法"""
        # 此时 message 已经是 AgentMessage 类型（codec解析过了）
        msg: AgentMessage = message

        logger.info("开始处理任务: %s", msg.task_id)
        try:
            handler = self.command_handlers.get(msg.command)
            if handler is None:
                logger.warning("未知 Agent command: %s", msg.command)
                return
            handler(msg)
        except Exception as e:
            logger.exception("任务 %s 执行失败", msg.task_id)

    def run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    def agent_report(self, agent: BaseAgent, event: AgentEvent, output: dict, runId: str, actionId: str = None):
        output = output if isinstance(output, dict) else {"output": output}
        msg = self.assemble(event=event, agent=agent, output=output, actionId=actionId)
        event_bus.publish("agent_status_topic", msg.model_dump_json(by_alias=True), runId)

    def assemble(self, event: AgentEvent, agent: BaseAgent, output, actionId) -> AgentMessage:
        """从当前 Agent 状态构造回传 Java 的消息（信封沿用原任务）"""
        msg = agent.base_message
        return AgentMessage(
            version="1.0",
            timestamp=int(time.time() * 1000),
            run_id=msg.run_id,
            task_id=msg.task_id,
            action_id=actionId,
            session_id=msg.session_id,
            message_id=str(uuid4()),
            type="AGENT_STATUS",  # 回传用独立 type，便于 Java 路由
            agent_name=agent.name,
            parent_agent=agent.parent_agent,
            event=event,
            step=agent.current_step,
            status=agent.status.value,
            output=output
        )

    def handle_start(self, msg):
        return self.run_manager.start(msg)

    def handle_resume(self, msg):
        return self.run_manager.resume(msg)

    def handle_retry(self, msg):
        return self.run_manager.retry(msg)

    def handle_cancel(self, msg):
        return self.run_manager.cancel(msg.task_id, msg.run_id)


agent_msg_service = AgentMsgService()
