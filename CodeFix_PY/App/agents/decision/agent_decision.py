# App/agents/agent_decision.py

from typing import Any, Literal, Annotated, Union

from pydantic import BaseModel, Field


class ToolCallDecision(BaseModel):
    """
    LLM 决定调用工具。
    """

    type: Literal["tool_call.py"]
    reason: str = ""
    tool: str
    arguments: dict[str, Any] = Field(default_factory=dict)


class FinishDecision(BaseModel):
    """
    LLM 决定结束当前 Agent。
    """

    type: Literal["finish"]
    reason: str = ""
    answer: dict[str, Any] = Field(default_factory=dict)


AgentDecision = Annotated[
    Union[
        ToolCallDecision,
        FinishDecision,
    ],
    Field(discriminator="type"),
]