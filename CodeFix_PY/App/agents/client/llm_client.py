from abc import ABC, abstractmethod

from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.llm_response import LLMResponse
from App.agents.memory.token_estimator import TokenEstimator


class LLMClient(TokenEstimator, ABC):

    @abstractmethod
    async def chat(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> LLMResponse:
        raise NotImplementedError

    @abstractmethod
    def context_window(self) -> int:
        raise NotImplementedError
