from typing import Any

from pydantic import Field

from App.infrastructure.message.base_message import BaseMessage
from App.models.enum.agent_command import AgentCommandType


class AgentCommandMessage(BaseMessage):
    command_type: AgentCommandType = Field(alias="commandType")
    run_id: str = Field(default="", alias="runId")
    command: str = ""
    action_id: str | None = Field(default=None, alias="actionId")
    payload: dict[str, Any] = Field(default_factory=dict)