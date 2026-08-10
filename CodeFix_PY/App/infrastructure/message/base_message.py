# App/infrastructure/message/base_message.py
from pydantic import BaseModel, Field
import time


class BaseMessage(BaseModel):
    """
    消息基类（信封）
    负责：传输元数据、路由、序列化
    """
    version: str = "1.0"
    timestamp: int = Field(default_factory=lambda: int(time.time() * 1000))
    message_id: str = Field(default="", alias="messageId")
    session_id: str = Field(default="", alias="sessionId")
    task_id: str = Field(default="", alias="taskId")
    type: str = ""

    # ===== 通用方法 =====
    @classmethod
    def to_json(self) -> str:
        """序列化为JSON字符串（发给MQ）"""
        return self.model_dump_json(by_alias=True, exclude_none=True)

    @classmethod
    def from_json(cls, json_str: str) -> "BaseMessage":
        """从JSON字符串反序列化"""
        return cls.model_validate_json(json_str)

    class Config:
        populate_by_name = True  # 同时支持 snake_case 和 camelCase 赋值
