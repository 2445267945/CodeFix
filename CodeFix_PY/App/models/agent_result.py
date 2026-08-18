from typing import Any, Dict, Optional
from pydantic import BaseModel, Field


class AgentResult(BaseModel):
    success: bool
    agent_name: str
    result: dict[str, Any] | str = ""
    error: str | None = None
    metadata: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def ok(cls, agent_name: str, result: Dict[str, Any], iterations: int,
           metadata: Optional[Dict[str, Any]] = None) -> "AgentResult":
        return cls(success=True, agent_name=agent_name, result=result, iterations=iterations, metadata=metadata or {})

    @classmethod
    def fail(cls, agent_name: str, result: Dict[str, Any], iterations: int,
             metadata: Optional[Dict[str, Any]] = None) -> "AgentResult":
        return cls(success=False, agent_name=agent_name, result=result, iterations=iterations, metadata=metadata or {})
