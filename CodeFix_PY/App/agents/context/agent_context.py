from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class AgentContext:
    main_llm: Any
    compress_llm: Any
    msg_sender: Any