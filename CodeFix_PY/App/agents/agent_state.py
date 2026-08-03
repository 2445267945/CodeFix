from enum import Enum

class AgentState (Enum):
    IDLE = "空闲状态"
    RUNNING = "运行状态"
    FINISHED = "完成状态"
    ERROR = "错误状态"