# App/infrastructure/mq/handler.py
import logging
from typing import Dict
from App.infrastructure.message.base_message import BaseMessage
from App.infrastructure.message.messagec import MessageCodec
from App.services.base_handler import BaseMsgHandler

logger = logging.getLogger(__name__)


class Handler:
    """
    消息路由器：只负责解析和分发，不处理任何业务
    """

    def __init__(self):
        # type → service 的映射表
        # TODO 可以改成支持自己注册
        self.handlers: Dict[str, BaseMsgHandler] = {}

    def register(self, msg_type: str, handler: BaseMsgHandler):
        """注册处理器"""
        self.handlers[msg_type] = handler
        logger.info(f"注册消息处理器: {msg_type} → {handler.__class__.__name__}")

    def handle(self, raw_json: str):
        """
        统一入口：解析 → 路由 → 分发
        """
        # 1. 解析消息（根据type得到具体子类）
        message = MessageCodec.decode(raw_json)

        # 2. 根据type找到对应的handler
        handler = self.handlers.get(message.type)

        if handler is None:
            logger.warning(f"未找到类型 [{message.type}] 的处理器，消息被丢弃")
            return

        # 3. 分发
        handler.handle(message)
