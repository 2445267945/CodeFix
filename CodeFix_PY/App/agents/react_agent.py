from abc import abstractmethod

from .base_agent import BaseAgent
from .agent_state import AgentState

class ReActAgent(BaseAgent):
    async def step(self):
        should_act = await self.think()
        if not should_act:
            # 检查是否真正完成了
            if self.status == AgentState.FINISHED:
                return "任务已经完成"
            else:
                # 没有有效指令但任务未完成，返回空，让循环继续（但提示词已引导）
                return "等待下一步指令"
        return await self.act()

    @abstractmethod
    async def think(self):
        pass
    @abstractmethod
    async def act(self):
        pass