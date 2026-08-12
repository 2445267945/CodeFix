# App/infrastructure/message/agent_message.py
from typing import Optional, Any
from pydantic import Field
from App.infrastructure.message.base_message import BaseMessage


class AgentMessage(BaseMessage):
    """
    Agent消息（信纸）
    双向复用：Java填question/code，Python填agentName/status/thought/output
    """

    # ===== Java → Python 时填充 =====
    question: str = ""
    code: str = ""
    smells: list = []
    # ===== Python → Java 时填充 =====
    event: str = ""
    step: int = 0
    agent_name: str = Field(default="", alias="agentName")
    parent_agent: Optional[str] = Field(default=None, alias="parentAgent")
    status: str = ""
    thought: str = ""
    output: dict = Field(default_factory=dict)


