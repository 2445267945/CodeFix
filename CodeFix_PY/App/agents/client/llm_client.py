from abc import ABC, abstractmethod

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse


class LLMClient(ABC):

    @abstractmethod
    async def chat(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> LLMResponse:
        raise NotImplementedError
