from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class ToolCall:
    """
    工具调用描述。
    """
    id: str
    name: str
    arguments: dict[str, Any] = field(default_factory=dict)
