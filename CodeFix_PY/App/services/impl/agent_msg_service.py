import logging
import asyncio
import threading
import time
from App.agents.base_agent import BaseAgent
from App.agents.context.agent_context import AgentContext
from App.agents.supervisor.supervisor_agent import SupervisorAgent
from App.infrastructure.message.base_message import BaseMessage
from App.infrastructure.mq.event_bus import event_bus
from App.models.agent_message import AgentMessage
from App.services.base_handler import BaseMsgHandler
from App.services.llm_factory import llm_factory

logger = logging.getLogger(__name__)
main_llm = llm_factory.get_llm(4096, 0.1, "mid")
compress_llm = llm_factory.get_llm(4096, 0.1, "low")

class AgentMsgService(BaseMsgHandler):
    """处理 AGENT_MSG 类型的消息"""
    def __init__(self):
        self.context = AgentContext(main_llm = main_llm, compress_llm = compress_llm, msg_sender = self)
        self.loop = asyncio.new_event_loop()
        self.thread = threading.Thread(
            target=self.run_loop,
            daemon=True
        )
        self.thread.start()
    def handle(self, message: BaseMessage) -> None:
        """统一入口方法"""
        # 此时 message 已经是 AgentMessage 类型（codec解析过了）
        msg: AgentMessage = message


        logger.info(f"开始处理任务: {msg.task_id}")
        print(f"开始处理任务: {msg.task_id}")

        try:
            # 执行主Agent推理
            future = asyncio.run_coroutine_threadsafe(self.run_agent(msg), self.loop)
        except Exception as e:
            logger.error(f"任务 {msg.task_id} 执行失败: {e}")
            event_bus.publish(AgentMessage.status_report(
                session_id=msg.session_id,
                task_id=msg.task_id,
                status="ERROR",
                thought=f"执行异常: {str(e)}"
            ))

    def run_loop(self):
        asyncio.set_event_loop(self.loop)
        self.loop.run_forever()

    async def run_agent(self, msg: AgentMessage) -> None:
        agent = SupervisorAgent(context=self.context, base_message=msg)
        await agent.run(msg.question)

    def agent_report(self, agent: BaseAgent, event: str, output: dict):
        output = output if isinstance(output, dict) else {"content": output}
        msg = self.assemble(event=event, agent=agent, output=output)
        event_bus.publish("agent_status_topic", msg.model_dump_json(by_alias=True))

    def assemble(self, event, agent: BaseAgent, output) -> AgentMessage:
        """从当前 Agent 状态构造回传 Java 的消息（信封沿用原任务）"""
        msg = agent.base_message
        return AgentMessage(
            version = "1.0",
            timestamp = int(time.time()),
            task_id = msg.task_id,
            session_id = msg.session_id,
            type = "AGENT_STATUS",  # 回传用独立 type，便于 Java 路由
            agent_name = agent.name,
            event = event,
            step = agent.current_step,
            status = agent.status.value,
            output = output
        )


agent_msg_service = AgentMsgService()
