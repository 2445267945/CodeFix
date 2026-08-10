package com.xd.mq;

import com.xd.service.TaskDispatcher;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MQListener implements MessageListenerConcurrently {

    @Autowired
    private TaskDispatcher taskDispatcher;

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            String json = new String(msg.getBody());
            taskDispatcher.dispatch(json);
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

}
