import asyncio
import time
from typing import Awaitable, TypeVar

T = TypeVar("T")

class TimeoutManager:
    """
    单个 Agent 实例独立持有的超时管理器。

    负责：
    1. 记录 Agent 最近一次有效活动
    2. 管理当前执行阶段
    3. 对 LLM / Tool 等真正执行过程施加超时
    4. 区分“正在执行”和“合法等待”
    """
    LLM = "LLM"
    TOOL = "TOOL"
    def __init__(self, llm_timeout: float = 60.0, tool_timeout: float = 120.0,):
        self.llm_timeout = llm_timeout
        self.tool_timeout = tool_timeout
        # 最近一次有效活动时间
        self.last_activity_at = time.monotonic()
        # 当前执行阶段，例如：
        # "LLM"
        # "TOOL"
        # "WAITING"
        self.current_phase: str | None = None
        # 当前阶段开始时间
        self.phase_started_at: float | None = None
        # 合法等待原因，例如：
        # "WAITING_APPROVAL"
        # "WAITING_EXTERNAL_RESULT"
        self.waiting_reason: str | None = None

    # Activity
    def mark_activity(self) -> None:
        """
        记录一次 Agent 的有效活动。
        """
        self.last_activity_at = time.monotonic()

    # Phase
    def start_phase(self, phase: str) -> None:
        """
        开始一个需要关注执行时间的阶段。
        """
        self.current_phase = phase
        self.phase_started_at = time.monotonic()
        self.waiting_reason = None

    def finish_phase(self) -> None:
        """
        当前执行阶段结束。
        """
        self.current_phase = None
        self.phase_started_at = None

    # Waiting
    def enter_waiting(self, reason: str) -> None:
        """
        进入合法等待状态。

        例如：
        - WAITING_APPROVAL
        - WAITING_EXTERNAL_RESULT

        合法等待不会被普通执行超时误判。
        """
        self.current_phase = "WAITING"
        self.phase_started_at = None
        self.waiting_reason = reason

    def leave_waiting(self) -> None:
        """
        结束等待状态，重新进入正常执行流程。
        """
        self.waiting_reason = None
        self.current_phase = None
        self.phase_started_at = None
        self.mark_activity()

    @property
    def is_waiting(self) -> bool:
        return self.waiting_reason is not None

    # Duration
    def activity_elapsed(self) -> float:
        """
        距离最近一次有效活动经过的秒数。
        """
        return time.monotonic() - self.last_activity_at

    def phase_elapsed(self) -> float | None:
        """
        当前执行阶段已经持续的秒数。

        如果当前没有执行阶段，则返回 None。
        """
        if self.phase_started_at is None:
            return None

        return time.monotonic() - self.phase_started_at

    # Timeout
    async def execute(self, awaitable: Awaitable[T], *, phase: str, timeout: float | None = None,) -> T:
        """
        执行一个受 timeout 控制的异步操作。

        timeout=None 时：
        使用当前 phase 对应的默认 timeout。

        例如：
            phase="LLM"
            -> self.llm_timeout

            phase="TOOL"
            -> self.tool_timeout
        """
        self.start_phase(phase)

        if timeout is None:
            if phase == self.LLM:
                timeout = self.llm_timeout
            elif phase == self.TOOL:
                timeout = self.tool_timeout

        try:
            if timeout is None:
                result = await awaitable
            else:
                result = await asyncio.wait_for(awaitable, timeout=timeout)
            self.mark_activity()
            return result

        finally:
            self.finish_phase()