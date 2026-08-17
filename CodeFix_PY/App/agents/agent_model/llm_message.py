from dataclasses import dataclass, field
from typing import Any, Optional

from App.agents.agent_model.tool_call import ToolCall


@dataclass
class LLMMessage:
    """
    Agent 内部使用的模型无关消息。
    """
    role: str
    content: Optional[str] = None
    reasoning_content: Optional[str] = None
    tool_calls: list[ToolCall] = field(default_factory=list)
    tool_call_id: Optional[str] = None
    name: Optional[str] = None
    extra: dict[str, Any] = field(default_factory=dict)