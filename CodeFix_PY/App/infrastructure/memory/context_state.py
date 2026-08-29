from dataclasses import dataclass, field

from App.agents.agent_model.llm_message import LLMMessage
from App.models.session_context import SessionContext


@dataclass
class ContextState:
    session_context: SessionContext | None = None
    messages: list[LLMMessage] = field(default_factory=list)
    history_summary: str = ""