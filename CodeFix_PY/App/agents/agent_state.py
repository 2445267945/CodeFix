from enum import Enum

# app/agents/agent_state.py
from enum import Enum


class AgentState(Enum):
    """
    Python Agent 内部微观状态（对齐 Java 宏观状态机）

    状态映射关系（Java宏观态 ← Python微观态）：
    - JAVA.AGENT_THINKING  ←  PYTHON.THINKING（LLM推理中）
    - JAVA.TOOL_CALLING   ←  PYTHON.EXECUTING（工具执行中）
    - JAVA.WAITING_VALIDATION ← PYTHON.VALIDATING（等待Java端编译校验，总之就是等待调用api的结果）
    - JAVA.WAITING_RETRY  ←  PYTHON.PENDING_RETRY（等待重试）
    - JAVA.COMPLETED      ←  PYTHON.FINISHED（成功完成）
    - JAVA.NEED_RETRY     ←  PYTHON.ERROR（异常退出，需要重试）
    """
    # Agent 尚未开始运行
    IDLE = "IDLE"
    # 正在调用 LLM 进行推理
    THINKING = "THINKING"
    # 正在执行 Tool
    EXECUTING = "EXECUTING"
    # 任务被取消
    CANCELLED = "CANCELLED"
    # 正在等待外部系统返回
    BLOCKED = "BLOCKED"
    # Agent 正常完成
    FINISHED = "FINISHED"
    # Agent 执行异常
    ERROR = "ERROR"

    @classmethod
    def from_name(cls, name: str) -> "AgentState":
        """按枚举名解析：from_name('THINKING') → AgentState.THINKING"""
        try:
            return cls[name.upper()]
        except KeyError:
            raise ValueError(f"未知 AgentState 名称: {name}")

    @classmethod
    def from_value(cls, value: str) -> "AgentState":
        """按中文 value 解析：from_value('推理中') → AgentState.THINKING"""
        for state in cls:
            if state.value == value:
                return state
        raise ValueError(f"未知 AgentState 值: {value}")
