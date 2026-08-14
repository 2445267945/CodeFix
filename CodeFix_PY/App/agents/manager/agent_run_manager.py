import asyncio

from App.agents.agent_state import AgentState
from App.agents.context.agent_context import AgentContext
from App.agents.supervisor.supervisor_agent import SupervisorAgent
from App.models.enum.agent_event import AgentEvent
from App.models.agent_message import AgentMessage
from App.models.agent_running import AgentRunning


class AgentRunManager:

    def __init__(self, loop: asyncio.AbstractEventLoop, context: AgentContext):
        self.loop = loop
        self.context = context
        self.running: dict[str, AgentRunning] = {}

    def start(self, msg: AgentMessage):
        """
        从消息创建并启动一个 Agent Run。
        这个方法可以从 MQ 线程调用。
        """
        future = asyncio.run_coroutine_threadsafe(self.run(msg), self.loop)
        return future

    def resume(self, msg: AgentMessage):
        return asyncio.run_coroutine_threadsafe(self.run(msg, resume=True), self.loop)

    async def run(self, msg: AgentMessage, resume: bool = False):
        agent = SupervisorAgent(context=self.context, base_message=msg, parent_agent=None)
        task = asyncio.current_task()
        run = AgentRunning(
            task_id=msg.task_id,
            run_id=msg.run_id,
            session_id=msg.session_id,
            agent=agent,
            task=task,
            cancel_event=asyncio.Event()
        )

        self.running[msg.task_id] = run

        try:
            await agent.run(msg.question, resume)
        except asyncio.CancelledError:
            await self.handle_cancelled(run)
            raise
        except Exception:
            print("Agent执行异常 taskId=%s", msg.task_id)
            raise
        finally:
            self.running.pop(msg.task_id, None)

    async def handle_cancelled(self, run: AgentRunning):
        agent = run.agent
        agent.status = AgentState.CANCELLED
        await agent.checkpoint_working_memory()
        agent.msg_sender.agent_report(
            agent=agent,
            event=AgentEvent.ERROR,
            output={"reason": "TASK_CANCELLED"}
        )

    def cancel(self, task_id: str, run_id: str) -> bool:
        run = self.running.get(task_id)
        if run is None:
            return False
        if run.run_id != run_id:
            return False
        # 跨线程取消
        self.loop.call_soon_threadsafe(run.task.cancel)
        return True

    # 本指重新执行start，用run_id区别同一个任务的两次执行
    def retry(self,  msg: AgentMessage):
        self.start(msg)

    def get(self, task_id: str):
        return self.running.get(task_id)

    def is_running(self, task_id: str) -> bool:
        return task_id in self.running

    def remove(self, task_id: str, run_id: str) -> bool:
        run = self.running.get(task_id)
        if run is None:
            return False
        if run.run_id != run_id:
            return False
        self.running.pop(task_id, None)
        return True

