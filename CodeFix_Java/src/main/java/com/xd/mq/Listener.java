package com.xd.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.service.AuditService;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Listener implements MessageListenerConcurrently {

    @Autowired
    private AuditService auditService;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String json = new String(msg.getBody());
            System.out.println("监听消息：" + json);
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

}
