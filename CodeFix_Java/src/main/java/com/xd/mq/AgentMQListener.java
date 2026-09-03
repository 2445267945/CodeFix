package com.xd.mq;

import com.xd.service.TaskDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class AgentMQListener implements MessageListenerOrderly {

    @Autowired
    private TaskDispatcher taskDispatcher;

    @Override
    public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs, ConsumeOrderlyContext context) {
        for (MessageExt msg : msgs) {
            String json = new String(msg.getBody(), StandardCharsets.UTF_8);

            log.info(
                    "[MQ RECEIVE] thread={}, msgId={}, topic={}, queueId={}, queueOffset={}, reconsumeTimes={}",
                    Thread.currentThread().getName(),
                    msg.getMsgId(),
                    msg.getTopic(),
                    msg.getQueueId(),
                    msg.getQueueOffset(),
                    msg.getReconsumeTimes()
//                    json
            );

            taskDispatcher.dispatch(json);
        }

        return ConsumeOrderlyStatus.SUCCESS;
    }
}

//@Component
//public class MQListener implements MessageListenerConcurrently {
//
//    @Autowired
//    private TaskDispatcher taskDispatcher;
//
//    @Override
//    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
//        for (MessageExt msg : msgs) {
//            String json = new String(msg.getBody());
//            taskDispatcher.dispatch(json);
//        }
//        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
//    }
//
//}
