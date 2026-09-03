import asyncio
import logging
import time
from dataclasses import dataclass
from uuid import uuid4

from App.infrastructure.mq.event_bus import event_bus
from App.models.agent_heartbeat import AgentHeartbeat

logger = logging.getLogger(__name__)


@dataclass
class HeartbeatRuntime:
    run_id: str
    task: asyncio.Task


class AgentHeartbeatService:

    def __init__(self):
        self.tasks: dict[str, HeartbeatRuntime] = {}
        self.heartbeat_time = 5

    async def start(self, task_id: str, run_id: str) -> None:
        current = self.tasks.get(task_id)
        # 同一个 Task + 同一个 Run 已经在运行
        if current is not None and current.run_id == run_id:
            return
        # 同一个 Task 已经有旧 Run 的 heartbeat
        if current is not None:
            current.task.cancel()
            try:
                await current.task
            except asyncio.CancelledError:
                pass

        heartbeat_task = asyncio.create_task(self.heartbeat_loop(task_id, run_id))
        self.tasks[task_id] = HeartbeatRuntime(run_id=run_id, task=heartbeat_task)

    async def stop(self, task_id: str, run_id: str) -> None:
        current = self.tasks.get(task_id)
        if current is None:
            return
        # 防止旧 Run 把新 Run 的 heartbeat 停掉
        if current.run_id != run_id:
            return
        self.tasks.pop(task_id, None)
        current.task.cancel()
        try:
            await current.task
        except asyncio.CancelledError:
            pass

    async def heartbeat_loop(self, task_id: str, run_id: str) -> None:
        seq = 0

        while True:
            try:
                seq += 1
                heartbeat = AgentHeartbeat(
                    timestamp=int(time.time() * 1000),
                    task_id=task_id,
                    run_id=run_id,
                    message_id=str(uuid4()),
                    seq=seq,
                )

                event_bus.publish("agent_heartbeat_topic", heartbeat.to_json())
                await asyncio.sleep(self.heartbeat_time)
            except asyncio.CancelledError:
                logger.debug("Heartbeat stopped: taskId=%s, runId=%s", task_id, run_id)
                raise
            except Exception:
                logger.exception("Task heartbeat 发送失败: taskId=%s, runId=%s", task_id, run_id)
                await asyncio.sleep(self.heartbeat_time)