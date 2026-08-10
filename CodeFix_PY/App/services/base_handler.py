from abc import ABC, abstractmethod
from App.infrastructure.message.base_message import BaseMessage


class BaseMsgHandler(ABC):
    """所有消息处理服务的统一接口"""

    @abstractmethod
    def handle(self, message: BaseMessage) -> None:
        pass
