package com.xd.mq;

import com.xd.exception.MqSendException;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MQProducer {

    @Autowired
    private DefaultMQProducer producer;

    public void send(String topic, String tags, String payload) {

        // 设置消息的topic,tag以及消息体
        Message msg = new Message(topic, tags, payload.getBytes(StandardCharsets.UTF_8));
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic 不能为空");
        }
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("消息体不能为空");
        }
        try {
            // 超时时间 10 秒（单位毫秒）
            SendResult result = producer.send(msg, 10000);
            log.info("消息发送成功，MsgId: {}, QueueId: {}", result.getMsgId(), result.getMessageQueue().getQueueId());
        } catch (MQBrokerException e) {
            log.error("RocketMQ Broker 异常，错误码: {}", e.getResponseCode(), e);
            throw new MqSendException("Broker 处理失败", e);
        } catch (RemotingException e) {
            log.error("RPC 通信异常", e);
            throw new MqSendException("网络连接失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 重置中断标志
            throw new MqSendException("发送被中断", e);
        } catch (MQClientException e) {
            log.error("客户端异常，错误码: {}", e.getResponseCode(), e);
            throw new MqSendException("客户端配置错误", e);
        }
    }
}
