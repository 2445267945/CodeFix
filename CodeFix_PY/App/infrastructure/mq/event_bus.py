from rocketmq.v5.client import ClientConfiguration
from rocketmq.v5.producer import Producer
from rocketmq.v5.model import Message
from rocketmq import Credentials
from App.config import config


class EventBus:
    def __init__(self, endpoints: str):
        # 1. 配置客户端
        credentials = Credentials("", "")  # 未开启认证时使用
        mq_config = ClientConfiguration(endpoints=endpoints, credentials=credentials)
        # 2. 创建生产者实例
        # Producer 的构造函数需要 ClientConfiguration 和一个 topic 元组
        self.producer = Producer(mq_config)
        # 3. 启动生产者
        self.producer.startup()

    def publish(self, topic: str, payload: dict) -> None:
        """发送消息"""
        msg = Message()
        msg.topic = topic
        msg.body = str(payload).encode('utf-8')
        send_receipt = self.producer.send(msg)
        # print(f"消息发送成功，Topic: {topic}, MessageId: {send_receipt.message_id}")


event_bus = EventBus(endpoints=config.mq.ROCKETMQ_NAMESRV_ADDR)
