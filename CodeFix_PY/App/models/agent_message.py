# App/infrastructure/message/agent_message.py
from typing import Optional, Any
from pydantic import Field
from App.infrastructure.message.base_message import BaseMessage
from App.models.session_context import SessionContext


class AgentMessage(BaseMessage):
    """
    Agent消息（信纸）
    """

    # ===== Java → Python =====
    question: str = ""
    code: str = ""
    smells: list = Field(default_factory=list)
    workspace_id: str = Field(default="", alias="workspaceId")
    session_context: Optional[SessionContext] = Field(default=None, alias="sessionContext")

    # ===== Python → Java =====
    run_id: str = Field(default="", alias="runId")
    command: str = ""
    event: str = ""
    step: int = 0
    agent_name: str = Field(default="", alias="agentName")
    parent_agent: Optional[str] = Field(default=None, alias="parentAgent")
    status: str = ""
    thought: str = ""
    output: dict = Field(default_factory=dict)


