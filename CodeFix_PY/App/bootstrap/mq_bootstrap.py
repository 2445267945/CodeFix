from rocketmq import FilterExpression

from App.infrastructure.message.types import MESSAGE_TYPE_MAP
from App.infrastructure.mq.consumer import Consumer
from App.infrastructure.mq.handler import Handler
from App.services.agent_msg_service import AgentMsgService
from App.config import MQConfig


class MQBootstrap:

    def __init__(self):
        self.consumers = []

    def start(self):
        topics = MQConfig.CONSUMER_TOPICS
        group_name = MQConfig.CONSUMER_GROUP
        # 1. 校验配置中的消息类型是否有对应 Message Schema
        configured_types = set(MQConfig.MESSAGE_TYPES)
        supported_types = set(MESSAGE_TYPE_MAP)
        unknown = configured_types - supported_types
        if unknown:
            raise RuntimeError(f"配置中存在未知 MESSAGE_TYPE: {unknown}")

        subscriptions = {
            topic.strip(): FilterExpression("*")
            for topic in topics
        }
        handler = Handler()
        agent_msg_service = AgentMsgService()
        # TODO 可能会有多个msg_types和多个Handler，可以改成注册的方法
        handler.register("AGENT_TASK", agent_msg_service)
        handler.register("AGENT_COMMAND", agent_msg_service.command_service)
        # TODO 消费者也会有多个
        consumer = Consumer(topics=subscriptions, group=group_name, handler=handler.handle)
        consumer.start()
        self.consumers.append(consumer)
        return self.consumers

    def stop(self):
        for consumer in self.consumers:
            consumer.shutdown()
