from abc import abstractmethod

from .base_agent import BaseAgent
from .agent_state import AgentState
from .context.agent_context import AgentContext


class ReActAgent(BaseAgent):
    def __init__(self, context: AgentContext, base_message = None, parent_agent: str | None = None):
        super().__init__(context, base_message, parent_agent)

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