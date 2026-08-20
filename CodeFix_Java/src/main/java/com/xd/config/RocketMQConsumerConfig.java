package com.xd.config;

import com.xd.mq.MQListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Configuration
public class RocketMQConsumerConfig {
    // 配置项大家自定义即可
    @Value("${mq.rocketmq.name-server}")
    private String nameSrvAddr;

    @Value("${mq.rocketmq.consumer.groupName}")
    private String consumerGroup;

    @Value("#{'${mq.rocketmq.consumer.topics}'.split(',')}")
    private List<String> topicList;

    @Autowired
    private MQListener registerMessageListener;

    @Bean
    public DefaultMQPushConsumer getRocketMQConsumer() throws RuntimeException {

        if (StringUtils.isEmpty(consumerGroup)) {
            throw new RuntimeException("consumerGroup is null !!!");
        }
        if (StringUtils.isEmpty(nameSrvAddr)) {
            throw new RuntimeException("namesrvAddr is null !!!");
        }
        if (StringUtils.isEmpty(topicList)) {
            throw new RuntimeException("topics is null !!!");
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameSrvAddr);
        consumer.registerMessageListener(registerMessageListener);
        /**
         * 设置Consumer第一次启动是从队列头部开始消费还是队列尾部开始消费
         * 如果非第一次启动，那么按照上次消费的位置继续消费
         */
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        /**
         * 设置消费模型，集群还是广播，默认为集群
         */
        consumer.setMessageModel(MessageModel.CLUSTERING);
        /**
         * 设置一次消费消息的条数，默认为1条
         */
        consumer.setConsumeMessageBatchMaxSize(1);
        log.info(
                "RocketMQ Consumer started: group={}, instanceName={}, topics={}, nameSrv={}",
                consumerGroup,
                consumer.getInstanceName(),
                topicList,
                nameSrvAddr
        );
        try {
            /**
             * 设置该消费者订阅的主题和tag，如果是订阅该主题下的所有tag，则tag使用*；如果需要指定订阅该主题下的某些tag，则使用||分割，例如tag1||tag2||tag3
             */
            topicList.forEach(topic -> {
                try {
                    consumer.subscribe(topic, "*");
                } catch (MQClientException e) {
                    e.printStackTrace();
                }
            });
            consumer.start();
            log.info("consumer is start !!! groupName:{},topics:{},namesrvAddr:{}", consumerGroup, topicList, nameSrvAddr);
        } catch (MQClientException e) {
            log.error("consumer is start !!! groupName:{},topics:{},namesrvAddr:{}", consumerGroup, topicList, nameSrvAddr, e);
            throw new RuntimeException(e);
        }
        return consumer;
    }


}
