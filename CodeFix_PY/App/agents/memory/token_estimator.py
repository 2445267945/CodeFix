from abc import ABC, abstractmethod

from App.agents.agent_model.llm_message import LLMMessage


class TokenEstimator(ABC):

    @abstractmethod
    def count_messages(self, messages: list[LLMMessage], tools: list[dict] | None = None) -> int:
        raise NotImplementedError

    @abstractmethod
    def count_text(self, text: str) -> int:
        raise NotImplementedError