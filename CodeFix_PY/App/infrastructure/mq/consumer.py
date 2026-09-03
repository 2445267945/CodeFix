# App/infrastructure/mq/consumer.py
import threading
import time
import json
import logging
from rocketmq import ClientConfiguration, Credentials, SimpleConsumer, FilterExpression
from App.config import config

logger = logging.getLogger(__name__)


class Consumer:
    def __init__(self, topics: dict, group: str, handler):
        self.endpoints = config.mq.NAMESRV_ADDR
        self.topics = topics
        self.group = group
        self.handler = handler
        self.running = False

        credentials = Credentials("", "")
        mq_config = ClientConfiguration(self.endpoints, credentials)

        self.consumer = SimpleConsumer(mq_config, group, self.topics)

    def start(self):
        if self.running:
            logger.warning("Consumer 已在运行，忽略重复启动")
            return

        self.consumer.startup()
        self.running = True

        thread = threading.Thread(target=self.poll_loop, daemon=True)
        thread.start()
        logger.info(f"Consumer 已启动，监听 topic: {self.topics}, group: {self.group}")

    def poll_loop(self):
        while self.running:
            try:
                messages = self.consumer.receive(16, 60)  # max_num, invisible_seconds
                if not messages:
                    continue
                for msg in messages:
                    try:
                        body = msg.body.decode("utf-8")
                        logger.info(f"收到消息: {body[:200]}")
                        self.handler(body)
                        self.consumer.ack(msg)
                    except Exception as e:
                        logger.error(f"处理消息失败: {e}")
            except Exception as e:
                logger.error(f"拉取消息异常: {e}")
                time.sleep(3)

    def stop(self):
        self.running = False
        try:
            self.consumer.shutdown()
        except Exception:
            pass
        logger.info("Consumer 已停止")
