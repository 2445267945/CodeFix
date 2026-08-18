from enum import Enum


class AgentCommand(str, Enum):
    START = "START"
    RESUME = "RESUME"
    RETRY = "RETRY"
    CANCEL = "CANCEL"
