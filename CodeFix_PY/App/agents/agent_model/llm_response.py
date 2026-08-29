from dataclasses import dataclass, field
from typing import Optional, Any

from App.agents.agent_model.tool_call import ToolCall


@dataclass
class LLMResponse:
    content: str | None = None
    reasoning_content: str | None = None
    tool_calls: list[ToolCall] = field(default_factory=list)
    raw: object | None = None
    usage: dict | None = None

    @property
    def has_tool_calls(self) -> bool:
        return bool(self.tool_calls)

    @property
    def is_final(self) -> bool:
        return not self.tool_calls
