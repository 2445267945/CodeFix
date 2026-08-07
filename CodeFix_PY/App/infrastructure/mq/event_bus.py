from rocketmq.v5.client import ClientConfiguration
from rocketmq.v5.producer import Producer
from rocketmq.v5.model import Message

class EventBus:
    def __init__(self, endpoints: str, topic: str):
        self.topic = topic
        # 1. 配置客户端
        config = ClientConfiguration(endpoints=endpoints)
        # 2. 创建生产者实例
        # Producer 的构造函数需要 ClientConfiguration 和一个 topic 元组
        self.producer = Producer(config, (topic,))
        # 3. 启动生产者
        self.producer.startup()

    def publish(self, payload: dict) -> None:
        """发送消息"""
        # 1. 组装消息
        msg = Message()
        msg.topic = self.topic
        # 消息体需要是字节类型
        msg.body = str(payload).encode('utf-8')

        # 2. 发送消息（同步发送）
        #    send 方法返回一个 SendReceipt 对象
        send_receipt = self.producer.send(msg)
        print(f"消息发送成功，MessageId: {send_receipt.message_id}")

event_bus = EventBus(
    namesrv_addr=settings.ROCKETMQ_NAMESRV_ADDR,
    topic=settings.AGENT_STATUS_TOPIC
)