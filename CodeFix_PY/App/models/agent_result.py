from typing import Any

from pydantic import BaseModel, Field


class AgentResult(BaseModel):
    success: bool
    agent_name: str
    result: dict[str, Any] | str = ""
    error: str | None = None
    iterations: int = 0
    metadata: dict[str, Any] = Field(default_factory=dict)

    @classmethod
    def ok(
        cls,
        agent_name: str,
        result: dict[str, Any] | str = "",
        iterations: int = 0,
        metadata: dict[str, Any] | None = None,
    ) -> "AgentResult":
        return cls(
            success=True,
            agent_name=agent_name,
            result=result,
            iterations=iterations,
            metadata=metadata or {},
        )

    @classmethod
    def fail(
        cls,
        agent_name: str,
        result: dict[str, Any] | str = "",
        iterations: int = 0,
        error: str | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> "AgentResult":
        return cls(
            success=False,
            agent_name=agent_name,
            result=result,
            error=error,
            iterations=iterations,
            metadata=metadata or {},
        )