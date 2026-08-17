import json
from dataclasses import is_dataclass, asdict
from App.agents.agent_model.llm_message_serializer import (
    serialize_message,
    deserialize_message,
)
from App.agents.agent_model.llm_message import LLMMessage
from App.agents.agent_model.tool_call import ToolCall
from App.infrastructure.memory.working_memory import WorkingMemory
from App.infrastructure.memory.working_memory_store import WorkingMemoryStore
from App.infrastructure.redis.redis_client import RedisService


class RedisWorkingMemoryStore(WorkingMemoryStore):
    TTL = 60 * 60 * 6  # 6小时

    def __init__(self, redis_service: RedisService):
        self.redis = redis_service.client

    @staticmethod
    def _key(session_id: str, task_id: str, run_id: str, agent_name: str) -> str:
        return f"agent:wm:{session_id}:{task_id}:{run_id}:{agent_name}"

    """
    list[LLMMessage]
        ↓
    serialize_message()
        ↓
    list[dict]
        ↓
    json.dumps()
        ↓
    JSON string
        ↓
    Redis
    """
    async def save(self, memory: WorkingMemory) -> None:
        key = self._key(memory.session_id, memory.task_id, memory.run_id, memory.agent_name)
        recent_messages = [
            # 持久化前把LLMMessage转成可序列话对象
            serialize_message(message)
            for message in memory.recent_messages
        ]
        data = {
            "task_id": memory.task_id,
            "run_id": memory.run_id,
            "session_id": memory.session_id,
            "agent_name": memory.agent_name,
            "step": str(memory.step),
            "status": memory.status,
            "question": memory.question,
            "history_summary": memory.history_summary,
            "recent_messages": json.dumps(
                recent_messages,
                ensure_ascii=False
            )
        }
        await self.redis.hset(key, mapping=data)
        await self.redis.expire(key, self.TTL)

    """
    Redis
      ↓
    JSON string
      ↓
    json.loads()
      ↓
    list[dict]
      ↓
    deserialize_message()
      ↓
    list[LLMMessage]
    """
    async def load(self, session_id: str, task_id: str, run_id: str, agent_name: str) -> WorkingMemory | None:
        key = self._key(session_id, task_id, run_id, agent_name)
        data = await self.redis.hgetall(key)
        if not data:
            return None
        recent_messages_raw = json.loads(data.get("recent_messages", "[]"))
        recent_messages = [
            deserialize_message(item)
            for item in recent_messages_raw
        ]
        return WorkingMemory(
            run_id=data["run_id"],
            task_id=data["task_id"],
            session_id=data["session_id"],
            agent_name=data["agent_name"],
            step=int(data.get("step", 0)),
            status=data.get("status", ""),
            question=data.get("question", ""),
            history_summary=data.get("history_summary", ""),
            recent_messages=recent_messages
        )


    async def clear(self, session_id: str, task_id: str, run_id: str, agent_name: str) -> None:
        key = self._key(session_id, task_id, run_id, agent_name)
        await self.redis.delete(key)
