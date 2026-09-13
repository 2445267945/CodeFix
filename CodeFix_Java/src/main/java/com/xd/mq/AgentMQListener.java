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
            );

            try {
                taskDispatcher.dispatch(json);
            } catch (Exception e) {
                log.error(
                        "[MQ CONSUME ERROR] msgId={}, queueId={}, queueOffset={}",
                        msg.getMsgId(),
                        msg.getQueueId(),
                        msg.getQueueOffset(),
                        e
                );

                throw e;
            }
        }

        return ConsumeOrderlyStatus.SUCCESS;
    }
}

