import asyncio
import logging
import threading

from App.agents.agent_state import AgentState
from App.agents.context.agent_context import AgentContext
from App.agents.supervisor.supervisor_agent import SupervisorAgent
from App.models.enum.agent_event import AgentEvent
from App.models.agent_message import AgentMessage
from App.models.agent_running import AgentRunning
from App.infrastructure.heartbeat.agent_heartbeat_service import AgentHeartbeatService

logger = logging.getLogger(__name__)


class AgentRunManager:

    def __init__(self, loop: asyncio.AbstractEventLoop, context: AgentContext, heartbeat_service: AgentHeartbeatService):
        self.loop = loop
        self.context = context
        self.running: dict[str, AgentRunning] = {}
        self.running_lock = threading.RLock()
        self.heartbeat_service = heartbeat_service

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
        try:
            run_context = self.context.create_run_context(msg)
            agent = SupervisorAgent(context=self.context, run_context=run_context, base_message=msg, parent_agent=None)
            task = asyncio.current_task()
            run = AgentRunning(
                task_id=msg.task_id,
                run_id=msg.run_id,
                session_id=msg.session_id,
                agent=agent,
                task=task,
                cancel_event=asyncio.Event()
            )

            with self.running_lock:
                self.running[msg.run_id] = run
            # 启动 Task Heartbeat
            await self.heartbeat_service.start(task_id=msg.task_id, run_id=msg.run_id)

            try:
                if resume:
                    session_context = None
                else:
                    session_context = msg.session_context
                await agent.run(msg.question, resume, session_context)
            except asyncio.CancelledError:
                await self.handle_cancelled(run)
                raise
            except Exception as e:
                print("Agent执行异常 taskId={}", e)
                logger.info("Agent执行异常 taskId={}", e)
                raise
            finally:
                await self.heartbeat_service.stop(task_id=msg.task_id, run_id=msg.run_id)
                with self.running_lock:
                    self.running.pop(msg.run_id, None)
        except Exception as e:
            print("run manager启动异常：{}", e)
            logger.info("run manager启动异常：{}", e)

    async def handle_cancelled(self, run: AgentRunning):
        agent = run.agent
        agent.status = AgentState.CANCELLED
        await agent.checkpoint_working_memory()
        agent.msg_sender.agent_report(
            agent=agent,
            event=AgentEvent.ERROR,
            output={"reason": "TASK_CANCELLED"},
            runId=run.run_id
        )

    def cancel(self, task_id: str, run_id: str) -> bool:
        run = self.running.get(run_id)
        if run is None:
            return False
        if run.task_id != task_id:
            return False
        # 跨线程取消
        self.loop.call_soon_threadsafe(run.task.cancel)
        return True

    # 本指重新执行start，用run_id区别同一个任务的两次执行
    def retry(self, msg: AgentMessage):
        self.start(msg)

    def get(self, run_id: str):
        with self.running_lock:
            return self.running.get(run_id)

    def is_running(self, run_id: str) -> bool:
        return run_id in self.running

    def remove(self, task_id: str, run_id: str) -> bool:
        run = self.running.get(run_id)
        if run is None:
            return False
        if run.task_id != task_id:
            return False
        with self.running_lock:
            self.running.pop(run_id, None)
        return True
