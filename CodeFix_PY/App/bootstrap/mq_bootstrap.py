from lib2to3.pgen2.tokenize import group

from rocketmq import FilterExpression

from App.infrastructure.mq.consumer import Consumer
from App.infrastructure.mq.handler import Handler
from App.services.impl.agent_msg_service import AgentMsgService
from App.config import MQConfig


class MQBootstrap:

    def __init__(self):
        self.consumers = []

    def start(self):
        topics = MQConfig.ROCKETMQ_TOPIC.split(",")
        group_name = MQConfig.ROCKETMQ_GROUP_NAME
        msg_types = MQConfig.MESSAGE_TYPE
        subscriptions = {}
        for topic in topics:
            subscriptions[topic] = FilterExpression("*")
        handler = Handler()
        # TODO 可能会有多个msg_types和多个Handler，可以改成注册的方法
        handler.register(
            msg_types,
            AgentMsgService()
        )
        # TODO 消费者也会有多个
        consumer = Consumer(
            topics = subscriptions,
            group = group_name,
            handler = handler.handle
        )
        consumer.start()
        self.consumers.append(consumer)
        return self.consumers

    def stop(self):
        for consumer in self.consumers:
            consumer.shutdown()