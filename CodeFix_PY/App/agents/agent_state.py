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
    # ========== 运行态 ==========
    THINKING = "推理中"  # 正在调用 LLM 进行推理（CPU/GPU 密集）
    EXECUTING = "工具执行中"  # 正在执行工具函数（本地计算，不阻塞）

    # ========== 阻塞态（重点修改） ==========
    BLOCKED = "外部阻塞"  # ⭐ 统一表示正在等待外部响应（IO/网络）
    # 通过附加字段或日志来区分具体阻塞原因（校验、检索、LLM 调用等）

    # ========== 终态 ==========
    FINISHED = "完成"
    ERROR = "错误"
    IDLE = "空闲"