from enum import Enum
from typing import Any

from pydantic import Field

from App.infrastructure.message.base_message import BaseMessage


class AgentRunCommand(str, Enum):
    START = "START"
    RESUME = "RESUME"
    RETRY = "RETRY"
    CANCEL = "CANCEL"


class AgentActionCommand(str, Enum):
    APPROVE = "APPROVE"
    REJECT = "REJECT"


class AgentCommandType(str, Enum):
    RUN = "RUN"
    ACTION = "ACTION"
