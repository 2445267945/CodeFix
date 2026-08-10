import json
import logging
from App.infrastructure.message.base_message import BaseMessage
from App.infrastructure.message.types import MESSAGE_TYPE_MAP

logger = logging.getLogger(__name__)

class MessageCodec:

    @staticmethod
    def encode(message: BaseMessage) -> str:
        """对象 → JSON字符串"""
        return message.to_json()

    @staticmethod
    def decode(json_str: str) -> BaseMessage:
        """JSON字符串 → 具体子类对象"""
        d = json.loads(json_str)
        msg_type = d.get("type", "")

        target_class = MESSAGE_TYPE_MAP.get(msg_type)
        if target_class is None:
            logger.warning(f"未知消息类型: {msg_type}，返回BaseMessage")
            return BaseMessage.model_validate(d)

        return target_class.model_validate(d)
