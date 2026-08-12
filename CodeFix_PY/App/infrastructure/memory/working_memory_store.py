from abc import ABC, abstractmethod

from .working_memory import WorkingMemory


class WorkingMemoryStore(ABC):

    @abstractmethod
    async def save(self, memory: WorkingMemory) -> None:
        pass

    @abstractmethod
    async def load(self, session_id: str, task_id: str, agent_name: str) -> WorkingMemory | None:
        pass

    @abstractmethod
    async def clear(self, session_id: str, task_id: str, agent_name: str) -> None:
        pass