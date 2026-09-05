import asyncio
from enum import Enum


class ExecutionDecision(str, Enum):
    ALLOW = "ALLOW"
    DENY = "DENY"


class ExecutionGate:
    """
    Agent Runtime 执行闸门。

    Agent 在这里 await；
    Java 后续通过 AGENT_COMMAND -> APPROVE / REJECT
    唤醒对应的 Future。
    """

    def __init__(self):
        self.pending: dict[str, asyncio.Future] = {}
        self.loop: asyncio.AbstractEventLoop | None = None

    def get_loop(self) -> asyncio.AbstractEventLoop:
        loop = asyncio.get_running_loop()

        if self.loop is None:
            self.loop = loop
        elif self.loop is not loop:
            raise RuntimeError("ExecutionGate 不能跨不同 event loop 使用")
        return loop

    async def wait_for_decision(self, action_id: str) -> ExecutionDecision:
        """
        Agent 在这里暂停。

        注意：
        这是 asyncio await，不会阻塞整个 Python 线程。
        """
        loop = self.get_loop()
        if action_id in self.pending:
            raise RuntimeError(f"Action 已经处于等待状态: {action_id}")
        future = loop.create_future()
        self.pending[action_id] = future
        try:
            return await future
        finally:
            await self.pending.pop(action_id, None)

    def deny_all(self) -> int:
        """
        拒绝当前所有 pending action。
        返回实际唤醒的 action 数量。
        """
        action_ids = list(self.pending.keys())
        count = 0
        for action_id in action_ids:
            if self.resolve(action_id, ExecutionDecision.DENY):
                count += 1
        return count

    def resolve(self, action_id: str, decision: ExecutionDecision) -> bool:
        """
        由 Command Handler 调用。

        返回：
        True  -> 成功唤醒等待中的 Agent
        False -> 当前没有对应 pending action
        """
        future = self.pending.get(action_id)
        if future is None:
            return False
        if future.done():
            return False
        loop = self.loop
        if loop is None or loop.is_closed():
            return False
        loop.call_soon_threadsafe(future.set_result, decision)
        return True

    def contains(self, action_id: str) -> bool:
        return action_id in self.pending

    def pending_count(self) -> int:
        return len(self.pending)
