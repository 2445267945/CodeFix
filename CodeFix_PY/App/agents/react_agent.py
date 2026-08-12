from abc import abstractmethod

from .base_agent import BaseAgent
from .agent_state import AgentState
from .context.agent_context import AgentContext


class ReActAgent(BaseAgent):
    def __init__(self, context: AgentContext, base_message = None):
        super().__init__(context, base_message)

    async def step(self):
        should_act = await self.think()
        if not should_act:
            return
        return await self.act()

    @abstractmethod
    async def think(self):
        pass
    @abstractmethod
    async def act(self):
        pass