from pydantic import Field

from App.infrastructure.message.base_message import BaseMessage


class AgentHeartbeat(BaseMessage):
    """
    Agent Heartbeat 消息

    用于表明某个 Task 当前仍有活动中的 Agent Run。
    Heartbeat 的业务粒度是 Task，Run 用于标识当前执行实例。
    """
    run_id: str = Field(default="", alias="runId")
    # 当前 Task 的第几次心跳
    seq: int = 0
    # 固定消息类型
    type: str = "AGENT_HEARTBEAT"